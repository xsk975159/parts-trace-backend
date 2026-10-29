# 后端文档

当前说明以源码、配置和迁移脚本为依据。仓库根目录是同时包含 `parts-trace-backend/`、`parts-trace-frontend/` 的目录；后端文档中的源码路径相对于后端目录，Markdown链接相对于所在文件。

源码开发先读运行说明，并按[前端README](../../parts-trace-frontend/README.md)启动页面。使用已打包程序时，从[当前交付目录](../../output/README.md)进入；独立版默认端口18080，源码和普通版默认后端端口8080。

| 阅读顺序 | 文档 | 内容 |
| --- | --- | --- |
| 1 | [运行说明](运行说明.md) | 环境变量、数据库准备、初始账号、编译启动与常见错误 |
| 2 | [功能与数据说明](功能与数据说明.md) | 当前能力、批次与单件关系、权限、核心API和业务状态 |
| 3 | [部署说明](部署说明.md) | 组件端口、前端静态部署和链/对象存储模式 |
| 4 | [双平台接入说明](双平台接入说明.md) | 长安链及FISCO BCOS 3.x的SDK、账户、合约和中继配置 |
| 5 | [合约说明](../contracts/README.md) | DockerGo与Solidity源码、构建、初始化参数及方法 |
| 6 | [测试说明](测试说明.md) | 测试命令、独立HTTP环境、既有记录及未验证范围 |
| 7 | [多机构核验说明](多机构核验说明.md) | 联合签名、验证节点、增量建表、委员会工具及实现范围 |
| 8 | [Windows运行包说明](Windows运行包说明.md) | EXE启动、同端口页面、数据库与密钥配置、重新打包 |
| 9 | [Windows独立运行包说明](Windows独立运行包说明.md) | 内置Java及MySQL、自动初始化、数据保存与停止 |
| 10 | [异构链Docker环境](../deploy/heterogeneous/README.md) | 本机长安链、FISCO BCOS 3.x、自动部署及实链跨链验收 |

## 常用文件

| 路径 | 用途 |
| --- | --- |
| [pom.xml](../pom.xml) | Java目标版本与后端依赖 |
| [application.yml](../src/main/resources/application.yml) | 默认端口、环境变量及逐链平台配置 |
| [start-local.ps1](../scripts/start-local.ps1) | Windows本地配置检查和启动 |
| [check-schema.sql](../scripts/check-schema.sql) | 只读检查缺少的表、字段及索引 |
| [cross_chain_migration.sql](../src/main/resources/db/cross_chain_migration.sql) | 已有库的跨链基础表 |
| [V20261025__security_completion.sql](../src/main/resources/db/V20261025__security_completion.sql) | 已有库的安全功能与任务结构增量升级 |
| [V20261026__relay_confirmation.sql](../src/main/resources/db/V20261026__relay_confirmation.sql) | 已有库启用多机构核验时创建确认表 |
| [init.sql](../src/main/resources/db/init.sql) | 新库初始化，包含删除旧表的语句 |

前端用法见[前端README](../../parts-trace-frontend/README.md)，项目目录见[根目录README](../../README.md)。
