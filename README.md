# 零部件可信溯源后端

后端提供账号与权限、单件DID、生产/质检/物流追溯、可验证凭证、质量异常处置和双平台存证。技术栈为Spring Boot、MyBatis-Plus、MySQL及长安链/FISCO BCOS SDK，版本以[pom.xml](pom.xml)为准。

## 启动

命令在本目录执行。使用Java 8或Java 11、Maven及MySQL 8；已有数据库先按[运行说明](docs/运行说明.md)备份并完成增量升级，新库才使用初始化脚本。

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

默认端口为8080。脚本检查数据库结构，按需提示输入数据库密码，并将应用密钥加密保存到本机 `.local/`。也可配置环境变量后直接运行 `target/parts-trace-backend-1.0-SNAPSHOT.jar`。

## 目录与文档

| 目录 | 内容 |
| --- | --- |
| `src/main/` | Java源码、应用配置、SDK样例和数据库脚本 |
| `src/test/` | 后端自动化测试 |
| [contracts](contracts/README.md) | 长安链DockerGo与FISCO Solidity合约 |
| `scripts/` | 本地启动、结构检查、数据辅助和独立HTTP测试 |
| [docs](docs/README.md) | 当前使用说明及历史设计参考 |
| `target/` | 编译输出及测试结果，不作为源码提交 |

详细入口：[功能与数据](docs/功能与数据说明.md)、[部署](docs/部署说明.md)、[双平台接入](docs/双平台接入说明.md)、[测试](docs/测试说明.md)。

链模式默认 `db`，真实SDK模式为 `real`；对象存储独立选择 `db` 或 `ipfs`。模拟模式与实际链/IPFS部署的验证范围分别记录。
