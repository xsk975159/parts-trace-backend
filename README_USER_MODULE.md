# 用户管理模块 - 快速开始

## 项目启动

### 1. 数据库初始化

执行以下SQL脚本：
```bash
# 基础表结构
mysql -u root -p parts_trace < docs/database/user-management.sql

# 用户角色关联表
mysql -u root -p parts_trace < docs/database/user-role.sql
```

### 2. 配置文件修改

编辑 `src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/parts_trace
    username: root
    password: your_password

jwt:
  secret: your_secret_key_here
```

### 3. 启动项目

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/parts-trace-backend-1.0-SNAPSHOT.jar
```

### 4. 访问Swagger文档

浏览器访问: http://localhost:8080/swagger-ui.html

## 快速测试流程

### 步骤1: 注册用户

```bash
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "confirmPassword": "123456",
    "realName": "测试用户",
    "email": "test@example.com",
    "phone": "13800138000",
    "userType": "normal"
  }'
```

### 步骤2: 用户登录

```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456"
  }'
```

响应示例：
```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "username": "testuser",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400000
  }
}
```

### 步骤3: 获取用户信息（需要Token）

```bash
curl -X GET http://localhost:8080/api/user/info \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### 步骤4: 绑定DID身份

```bash
curl -X POST http://localhost:8080/api/did/bind \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

### 步骤5: 生成RSA密钥对

```bash
curl -X POST http://localhost:8080/api/rsa/generate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "我的密钥",
    "isDefault": 1,
    "expireDays": 365
  }'
```

## 核心功能说明

### 1. 用户类型

- **normal**: 普通用户 - 基础功能访问权限
- **supply_chain**: 供应链用户 - 供应链相关功能权限
- **regulator**: 监管机构 - 管理员权限，可访问所有接口

### 2. 认证流程

```
用户登录 → 获取Token → 携带Token访问接口 → 拦截器验证 → 业务处理
```

### 3. 权限控制

- 普通接口: 所有登录用户可访问
- `/api/admin/*`: 仅监管机构用户可访问
- `/api/supply-chain/*`: 供应链用户和监管机构可访问

### 4. DID身份体系

```
用户注册 → 绑定DID → 生成DID文档 → 存储公钥 → 身份验证
```

## 项目架构

```
┌─────────────┐
│   Controller │  ← 接口层（接收请求、返回响应）
└──────┬──────┘
       │
┌──────▼──────┐
│  Interceptor │  ← 拦截器层（JWT认证、权限验证）
└──────┬──────┘
       │
┌──────▼──────┐
│   Service   │  ← 业务逻辑层（核心业务处理）
└──────┬──────┘
       │
┌──────▼──────┐
│   Mapper    │  ← 数据访问层（数据库操作）
└──────┬──────┘
       │
┌──────▼──────┐
│   Database  │  ← 数据库（MySQL）
└─────────────┘
```

## 已实现的功能清单

✅ 用户注册（密码BCrypt加密）
✅ 用户登录（JWT Token生成）
✅ 用户信息管理（CRUD）
✅ 用户状态管理（启用/禁用）
✅ 角色权限分配
✅ DID身份绑定
✅ RSA密钥对生成与管理
✅ JWT认证拦截器
✅ 权限拦截器
✅ 全局异常处理
✅ 参数校验
✅ Swagger API文档

## 技术亮点

1. **密码安全**: 使用BCrypt加密，自动加盐，每次加密结果不同
2. **Token机制**: AccessToken + RefreshToken双Token设计
3. **DID标准**: 遵循W3C DID规范，支持去中心化身份
4. **RSA加密**: 2048位密钥，支持签名验证
5. **权限控制**: 基于用户类型的多级权限管理
6. **异常处理**: 统一异常处理，友好的错误提示
7. **参数校验**: 使用Validation注解，自动校验参数

## 下一步开发建议

1. **集成Redis**: 用于Token黑名单和缓存
2. **添加日志**: 使用AOP记录操作日志
3. **接口限流**: 防止恶意请求
4. **文件上传**: 用户头像上传功能
5. **邮件通知**: 注册验证、密码重置
6. **短信验证**: 手机号验证码登录

## 常用命令

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包项目
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests

# 运行项目
mvn spring-boot:run

# 查看依赖树
mvn dependency:tree
```

## 联系方式

如有问题，请查看：
- API文档: `docs/API.md`
- 开发指南: `docs/USER_MANAGEMENT_GUIDE.md`
- 数据库设计: `docs/database/`
