# 后端文档

当前说明以源码、配置和迁移脚本为依据。仓库根目录是同时包含 `parts-trace-backend/`、`parts-trace-frontend/` 的目录；后端文档中的源码路径相对于后端目录，Markdown链接相对于所在文件。

| 阅读顺序 | 文档 | 内容 |
| --- | --- | --- |
| 1 | [运行说明](运行说明.md) | 环境变量、数据库准备、初始账号、编译启动与常见错误 |
| 2 | [功能与数据说明](功能与数据说明.md) | 当前能力、批次与单件关系、权限、核心API和业务状态 |
| 3 | [部署说明](部署说明.md) | 组件端口、前端静态部署和链/对象存储模式 |
| 4 | [双平台接入说明](双平台接入说明.md) | 长安链及FISCO BCOS 3.x的SDK、账户、合约和中继配置 |
| 5 | [合约说明](../contracts/README.md) | DockerGo与Solidity源码、构建、初始化参数及方法 |
| 6 | [测试说明](测试说明.md) | 测试命令、独立HTTP环境、既有记录及未验证范围 |
| 参考 | [历史参考](历史参考/README.md) | 早期需求、模型草案和开发方案，不作为当前运行步骤 |

## 常用文件

| 路径 | 用途 |
| --- | --- |
| [pom.xml](../pom.xml) | Java目标版本与后端依赖 |
| [application.yml](../src/main/resources/application.yml) | 默认端口、环境变量及逐链平台配置 |
| [start-local.ps1](../scripts/start-local.ps1) | Windows本地配置检查和启动 |
| [check-schema.sql](../scripts/check-schema.sql) | 只读检查缺少的表、字段及索引 |
| [cross_chain_migration.sql](../src/main/resources/db/cross_chain_migration.sql) | 已有库的跨链基础表 |
| [V20261025__security_completion.sql](../src/main/resources/db/V20261025__security_completion.sql) | 已有库的安全功能与任务结构增量升级 |
| [init.sql](../src/main/resources/db/init.sql) | 新库初始化，包含删除旧表的语句 |

前端用法见[前端README](../../parts-trace-frontend/README.md)，项目目录见[根目录README](../../README.md)。
