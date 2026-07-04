# 零部件溯源 - 后端（Spring Boot）

## 文档（以 `docs/` 为准）

| 说明 | 路径 |
|------|------|
| **文档索引 / 任务总表** | [docs/文档索引.md](docs/文档索引.md)、[docs/项目工作与部署清单.md](docs/项目工作与部署清单.md) |
| **智能合约（Go→WASM）** | [contracts/合约说明.md](contracts/合约说明.md) |
| **组装与部署** | [docs/装配与部署指南.md](docs/装配与部署指南.md) |
| **论文 5.1 / 5.2 对照** | [docs/论文与实现对照-5.1-5.2.md](docs/论文与实现对照-5.1-5.2.md) |

## 快速启动（默认不依赖链 / IPFS）

1. 创建库 `parts_trace`，执行 `src/main/resources/db/init.sql`
2. 配置 `application.yml` 或通过环境变量设置 `KEWEI_DBUSER` / `KEWEI_DBPWD` 等
3. `mvn -DskipTests spring-boot:run`
4. Swagger：`http://localhost:8080/swagger-ui.html`

存储模式：`parts.trace.storage.chain-mode`（`db` | `chainmaker`）、`object-mode`（`db` | `ipfs`），见 `application.yml`。

## 技术栈摘要

Spring Boot 2.7、MyBatis-Plus、MySQL 8、可选 ChainMaker SDK（`chainmaker-sdk-java`）、JWT。
