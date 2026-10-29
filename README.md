# 工业零部件可信溯源后端

后端负责账号与权限、单件 DID、生产、质检和物流记录、可验证凭证、质量异常处置和跨链存证。采用 Spring Boot 2.7.0、MyBatis-Plus 和 MySQL 8，通过长安链 Java SDK 2.3.3、FISCO BCOS Java SDK 3.8.0 接入两类链；具体依赖以 [pom.xml](pom.xml) 为准。

## 环境与启动

本文命令在 `parts-trace-backend/` 内执行。编译支持 Java 8 或 Java 11，目标 API 为 Java 8；真实链与 Windows 独立版的最近验证使用 Java 11。准备 Maven 和 MySQL 8 后，先按[运行说明](docs/运行说明.md)初始化新库或升级已有数据库。

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

默认 API 地址为 `http://localhost:8080/api`，接口文档为 `http://localhost:8080/swagger-ui.html`。按 `Ctrl+C` 停止服务。只检查配置时，在启动命令末尾加 `-CheckOnly`。

启动脚本只检查数据库和表结构，不自动导入 SQL；未设置 `KEWEI_DBPWD` 时提示输入数据库密码。应用密钥通过 Windows DPAPI 加密保存在 `.local/startup-secrets.json`，后续启动使用原文件和 Windows 账户。配置好数据库、`JWT_SECRET` 和 `KEY_ENCRYPTION_KEY` 后，也可直接运行 `java -jar target/parts-trace-backend-1.0-SNAPSHOT.jar`。

普通 `mvn package` 用于后端构建。源码联调时按[前端 README](../parts-trace-frontend/README.md)另行启动 Vite；将前端静态文件嵌入 JAR 时，先构建前端，再使用 `mvn -Pwindows-package package`。Windows 打包脚本会完成这两步。

## 配置与链模式

| 配置 | 用途 |
| --- | --- |
| `KEWEI_DBSERVER / KEWEI_DBPORT / KEWEI_DBUSER / KEWEI_DBPWD` | MySQL 连接，默认库名 `parts_trace` |
| `JWT_SECRET` | 登录令牌签名密钥，至少 32 字符 |
| `KEY_ENCRYPTION_KEY` | 托管私钥加密主密钥，32 字节密钥的 Base64 编码 |
| `PARTS_TRACE_CHAIN_MODE` | `db` 为模拟账本，`real` 为真实 SDK；默认 `db` |
| `PARTS_TRACE_OBJECT_MODE` | `db` 为数据库对象存储，`ipfs` 为实际 IPFS；默认 `db` |

真实模式按逻辑链选择平台，不能仅修改开关而省略节点、身份、合约与 SDK 配置。配套[异构链 Docker 环境](deploy/heterogeneous/README.md)提供长安链 2.3.10、FISCO BCOS 3.16.3 和自动部署验收。可选[多机构核验](docs/多机构核验说明.md)默认关闭，启用时另配验证服务、委员会和确认表。

## 代码目录

| 目录 | 内容 |
| --- | --- |
| `src/main/java/com/kewei/parts/module/` | 用户、零部件、追溯、异常和统计业务 |
| `src/main/java/com/kewei/parts/infrastructure/` | 链驱动、中继、存储、多机构核验和桌面运行支持 |
| `src/main/resources/` | 默认配置、数据库脚本、SDK 与委员会样例 |
| `src/test/` | 后端测试与夹具 |
| [contracts](contracts/README.md) | 长安链 DockerGo、FISCO Solidity 合约及测试 |
| `scripts/` | 本地启动、数据库检查、HTTP 集成验证和 Windows 打包 |
| `packaging/windows/` | EXE 启动器、运行脚本及默认配置样例 |
| [deploy/heterogeneous](deploy/heterogeneous/README.md) | Docker 建链、部署、验收与镜像导出 |
| [docs](docs/README.md) | 开发、运行、部署和验证说明 |
| `target/` | 构建产物与测试结果 |

## 文档与验证

数据库、账号和启动故障从[运行说明](docs/运行说明.md)查阅；业务流程、接口及状态见[功能与数据说明](docs/功能与数据说明.md)；前后端部署方式见[部署说明](docs/部署说明.md)。

后端执行 `mvn test`；Go、Solidity、建链脚本及隔离 HTTP 测试的命令、前提和结果范围统一见[测试说明](docs/测试说明.md)。测试代码不要求启动业务链，真实节点验证按 Docker 部署说明单独执行。

已制作的可执行包位于[参赛运行包目录](../output/比赛资料/可执行文件/README.md)。普通版需要外部 Java 和 MySQL，独立版内置运行环境并自动初始化私有数据库；源码重新打包见[普通版说明](docs/Windows运行包说明.md)与[独立版说明](docs/Windows独立运行包说明.md)。
