# 本地异构链运行环境

本目录为 Windows 独立运行包提供长安链和 FISCO BCOS 节点。业务程序、前端和私有 MySQL 继续由独立运行包启动；链节点在 Docker Desktop 的 Linux 容器中运行。

## 网络组成

| 逻辑链 | 平台与账本 | 节点 |
| --- | --- | --- |
| 监管主链 `main-regulator` | 长安链 2.3.10，`chain1` | 四个 TBFT 共识节点 |
| 生产从链 `sub-production` | 长安链 2.3.10，`chain2` | 同一组四个节点分别维护独立账本 |
| 质检从链 `sub-quality` | 长安链 2.3.10，`chain3` | 同一组四个节点分别维护独立账本 |
| 物流从链 `sub-logistics` | FISCO BCOS 3.16.3 Air，`chain0/group0` | 四个 PBFT 节点 |

长安链另外运行四个 DockerGo 合约引擎，版本为 2.3.9。总计十二个常驻容器。长安链节点镜像保留官方 2.3.10 节点二进制及必需的 Wasmer 运行库；FISCO 镜像使用官方 3.16.3 节点发行包，沿用 3.8.0 兼容配置与 Java SDK。

这是单机演示网络，四个节点不代表四家真实独立运行的机构。跨链采用项目已有的授权中继签名机制，接收方检查签名后写入目标账本。本运行包未配置多机构联合核验服务，也未包含 IPFS；对象存储可继续使用数据库模式。

## 启动与停止

先安装 Docker Desktop，启用 WSL 2 和 Linux 容器。建议电脑具有 32 GB 内存，并为 Docker 留出至少 12 GB 内存和 10 GB 可用磁盘空间。实际用量随账本增长。

已交付的离线运行目录包含 `images.tar.gz`。双击 `start-chains.cmd` 后，会导入镜像、生成本机证书、启动节点、部署合约并执行验收。首次生成的身份和合约地址会保留，后续启动沿用。

将 `docker` 与独立版运行目录平级放置。脚本会自动识别 `standalone` 和压缩包中的 `工业零部件可信溯源平台-Windows-x64-独立版` 两种目录名，不需要改名。EXE 应直接位于该独立版目录内，避免重复嵌套解压。其他位置可在 PowerShell 中指定：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-chains.ps1 -ApplicationDirectory "D:\参赛作品\standalone"
```

只有部署和验收通过，脚本才会把独立版的 `chainMode` 改为 `real`。随后正常停止并重新启动“启动溯源平台.exe”。原有附加配置通过 Spring 配置导入保留；切换前的运行配置保存在 `runtime/runtime-before-chains.json`。

双击 `stop-chains.cmd` 停止本目录创建的链网络。停止脚本保留数据卷，不停止其他项目的容器，也不操作业务数据库。应用与链网络分开停止：先正常停止应用，再停止链节点。

本机只开放 `127.0.0.1:12301` 和 `127.0.0.1:20200`。节点之间在专用 Docker 网络通信，固定网段为 `172.29.67.0/24`。端口或网段冲突时，先核对占用来源，不要直接终止其他服务。

## 验收与数据

启动时使用应用的链驱动和路由执行以下检查：

- 确认四条逻辑链分别绑定到四条账本，且合约版本、逻辑链编号一致。
- 在每条账本写入并查询摘要，写入和撤销测试凭证。
- 核对源链交易后，将生产、质检和物流摘要提交至监管主链，并查询接收结果。
- 修改中继消息中的摘要，确认主链明确拒绝签名校验失败的消息，且原存证未改变。

测试使用 `docker_smoke` 业务类型，只在链上产生演示记录，不在业务数据库中创建零部件。检查结果及真实交易编号写入 `runtime/sdk/verification.json`，部署回执写入 `runtime/sdk/deployment.json`。

`runtime/sdk` 包含应用私钥，仅供本机运行使用，不应打入提交材料或另发给他人。根证书私钥和节点私钥保存在本项目的 Docker 数据卷中，启动脚本不导出这些私钥。转移运行环境时需同时保存原目录和相应数据卷，不能仅复制 SDK 文件后重新建链。

若部署中断并留下 `deploy-*.pending`，应先按交易和合约状态核查结果，再恢复部署。脚本不会自动清除标记、重新生成已有身份或删除账本。

长安链部署已成功但回执保存中断时，可在确认网络归属后运行 `docker --context desktop-linux compose -p <runtime/network.json中的project> -f compose.yaml --profile tools run --rm --no-deps prepare recover-cm`。该操作只查询合约和区块，核对部署交易及参数后补存回执，不再次提交部署；存在歧义时停止恢复。

## 从源码构建

在后端目录运行以下命令。需 Java 11、Maven、Docker Desktop，且前端已构建到 `parts-trace-frontend/dist`。构建过程需要访问官方基础镜像、操作系统软件源和 Go 模块代理；镜像制作完成后的离线启动不再下载这些依赖。

```powershell
mvn -Pwindows-package -Dparts.build.directory=target/heterogeneous-build package
New-Item -ItemType Directory -Force deploy/heterogeneous/assets
Copy-Item target/heterogeneous-build/parts-trace-backend-1.0-SNAPSHOT.jar deploy/heterogeneous/assets/parts-trace-backend.jar
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/heterogeneous/scripts/start-chains.ps1 -BuildImages -ApplicationDirectory "D:\参赛作品\standalone"
```

构建前，将官方 FISCO 发行包下载到 `vendor/fisco-bcos-linux-x86_64-v3.16.3.tar.gz`；下载地址和哈希见 [vendor/README.md](vendor/README.md)。Go 代理仅在构建参数中设置，可用 `GO_MODULE_PROXY` 构建参数覆盖，不修改主机全局配置。

镜像构建完成后运行 `powershell -NoProfile -ExecutionPolicy Bypass -File deploy/heterogeneous/scripts/package-chains.ps1`，在 `output/docker` 导出镜像、启动脚本及校验文件，并生成独立 ZIP。打包仅包含列出的发行文件，排除 `runtime` 中的私钥与本机配置。交付包使用自带镜像，不需要源码或构建工具。

参考：[Docker Desktop Windows 安装](https://docs.docker.com/desktop/setup/install/windows-install/)、[WSL 安装](https://learn.microsoft.com/en-us/windows/wsl/install)、[长安链 DockerGo 部署](https://docs.chainmaker.org.cn/instructions/启动支持Docker_VM的链.html)、[FISCO BCOS 3.16.3 发行页](https://github.com/FISCO-BCOS/FISCO-BCOS/releases/tag/v3.16.3)。
