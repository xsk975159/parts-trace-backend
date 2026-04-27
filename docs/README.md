# 零部件溯源系统 - 快速导航

## 📚 文档目录

### 1. 数据库设计文档

#### 1.1 概述文档
- [数据库设计概述](database/database-design-overview.md) - 设计原则、命名规范、表统计
- [ER图设计](database/ER-diagram-design.md) - 完整的ER图和表关系说明

#### 1.2 SQL脚本（按模块）
- [用户管理模块](database/01-user-management-tables.sql) - 7张表
- [零部件管理模块](database/02-parts-management-tables.sql) - 5张表
- [零部件溯源模块](database/03-parts-trace-tables.sql) - 8张表
- [流程异常检测模块](database/04-anomaly-detection-tables.sql) - 7张表
- [追溯查询模块](database/05-trace-query-tables.sql) - 4张表

#### 1.3 完整初始化脚本
- [完整数据库初始化脚本](database/init-database.sql) - 一键初始化所有表

### 2. 类图设计文档

- [类图设计（第一部分）](design/class-diagram-design.md) - 用户管理、零部件管理、零部件溯源模块
- [类图设计（第二部分）](design/class-diagram-part2.md) - 流程异常检测、追溯查询模块

### 3. API接口文档

- [API接口文档](API.md) - 完整的REST API接口说明
- [用户管理开发指南](USER_MANAGEMENT_GUIDE.md) - 详细的开发指南
- [快速开始](../README_USER_MODULE.md) - 快速上手指南

### 4. 系统设计文档

- [系统架构设计](architecture.md) - 系统整体架构
- [系统设计文档](system-design.md) - 详细设计说明
- [部署文档](deployment.md) - 部署指南

### 5. 总结文档

- [数据库与类图设计总结](DATABASE_AND_CLASS_DESIGN_SUMMARY.md) - 完整设计成果总结

## 🗂️ 数据库表清单（31张表）

### 用户管理模块（7张）
1. `sys_user` - 用户表
2. `sys_role` - 角色表
3. `sys_user_role` - 用户角色关联表
4. `sys_permission` - 权限表
5. `sys_role_permission` - 角色权限关联表
6. `sys_user_did` - 用户DID绑定表
7. `sys_user_rsa_key` - 用户RSA密钥表

### 零部件管理模块（5张）
8. `parts_category` - 零部件分类表
9. `parts_info` - 零部件基础信息表
10. `parts_did` - 零部件DID表
11. `parts_specification` - 零部件规格表
12. `blockchain_record` - 区块链上链记录表

### 零部件溯源模块（8张）
13. `trace_event` - 溯源事件表（主表）
14. `trace_production` - 生产事件表
15. `trace_quality` - 质检事件表
16. `trace_logistics` - 物流事件表
17. `verifiable_credential` - 可验证凭证表
18. `production_certificate` - 生产证明表
19. `quality_report` - 质检报告表
20. `transfer_certificate` - 流转证明表

### 流程异常检测模块（7张）
21. `business_rule` - 业务规则表
22. `time_rule` - 时效规则表
23. `process_rule` - 流程规则表
24. `quality_rule` - 质量规则表
25. `smart_contract` - 智能合约表
26. `anomaly_event` - 异常事件表
27. `anomaly_handling` - 异常处理记录表

### 追溯查询模块（4张）
28. `query_log` - 查询日志表
29. `trace_report` - 溯源报告表
30. `verification_record` - 真伪验证记录表
31. `counterfeit_report` - 假冒产品上报表

## 🎯 核心功能模块

### 1. 用户管理
- ✅ 用户注册与登录（JWT认证）
- ✅ 角色权限管理（RBAC）
- ✅ DID身份绑定
- ✅ RSA密钥对管理

### 2. 零部件管理
- ✅ 零部件基础信息管理
- ✅ 零部件分类管理
- ✅ 零部件DID生成
- ✅ 区块链上链管理

### 3. 零部件溯源
- ✅ 生产事件记录
- ✅ 质检事件记录
- ✅ 物流事件记录
- ✅ 可验证凭证签发

### 4. 流程异常检测
- ✅ 业务规则配置
- ✅ 智能合约部署
- ✅ 异常自动检测
- ✅ 异常处理流程

### 5. 追溯查询
- ✅ 溯源信息查询
- ✅ 溯源报告生成
- ✅ 真伪验证
- ✅ 假冒产品上报

## 🚀 快速开始

### 1. 初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE parts_trace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行初始化脚本
mysql -u root -p parts_trace < docs/database/init-database.sql
```

### 2. 配置应用

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/parts_trace
    username: root
    password: your_password

jwt:
  secret: your_secret_key
```

### 3. 启动项目

```bash
mvn spring-boot:run
```

### 4. 访问文档

- Swagger UI: http://localhost:8080/swagger-ui.html
- API文档: http://localhost:8080/v3/api-docs

## 📊 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端应用层                           │
│              (Vue.js / React / 小程序)                   │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/HTTPS
┌────────────────────▼────────────────────────────────────┐
│                  API网关层                               │
│           (Nginx / Spring Cloud Gateway)                │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                 应用服务层                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │用户管理  │ │零部件管理│ │溯源管理  │ │异常检测  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                 数据持久层                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐               │
│  │  MySQL   │ │  Redis   │ │   IPFS   │               │
│  └──────────┘ └──────────┘ └──────────┘               │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                 区块链层                                 │
│              长安链 ChainMaker                           │
└─────────────────────────────────────────────────────────┘
```

## 🔧 技术栈

- **后端框架**: Spring Boot 2.7.0
- **ORM框架**: MyBatis-Plus 3.4.2
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **区块链**: 长安链 ChainMaker
- **文件存储**: IPFS
- **安全认证**: JWT + BCrypt
- **身份标识**: DID (W3C标准)
- **加密算法**: RSA 2048位
- **API文档**: SpringDoc OpenAPI

## 📝 开发规范

### 命名规范
- **表名**: 小写字母，下划线分隔，统一前缀
- **字段名**: 小写字母，下划线分隔
- **类名**: 大驼峰命名法
- **方法名**: 小驼峰命名法

### 代码规范
- 遵循阿里巴巴Java开发手册
- 使用Lombok简化代码
- 统一异常处理
- 统一响应格式

### 数据库规范
- 所有表必须有主键
- 所有表必须有create_time和update_time
- 核心业务表支持逻辑删除
- 外键字段必须建立索引

## 🤝 贡献指南

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 📄 许可证

本项目采用 MIT 许可证

## 📧 联系方式

- 项目地址: https://github.com/your-repo/parts-trace-backend
- 问题反馈: https://github.com/your-repo/parts-trace-backend/issues
- 邮箱: support@example.com

---

**最后更新**: 2024-03-01  
**版本**: 1.0.0
