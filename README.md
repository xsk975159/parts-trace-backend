# 基于长安链的零部件溯源系统 - 后端

## 项目简介

基于Spring Boot + 长安链（ChainMaker）的零部件溯源系统后端应用，采用前后端分离架构。

## 技术栈

- **框架**: Spring Boot 2.7.0
- **区块链平台**: 长安链 ChainMaker v2.3.3
- **数据库**: MySQL 8.0
- **ORM框架**: MyBatis-Plus 3.4.2
- **连接池**: Druid 1.1.10
- **文件存储**: IPFS
- **API文档**: SpringDoc/Swagger
- **工具类**: Hutool、Lombok

## 项目结构

```
parts-trace-backend/
├── src/
│   └── main/
│       ├── java/com/kewei/parts/
│       │   ├── PartsTraceApplication.java    # 启动类
│       │   ├── config/                       # 配置类
│       │   │   ├── ChainmakerConfig.java    # 长安链配置
│       │   │   ├── GlobalCorsConfig.java    # 跨域配置
│       │   │   └── MybatisPlusConfig.java   # MyBatis-Plus配置
│       │   ├── controller/                   # 控制器
│       │   │   ├── PartsController.java     # 零部件管理
│       │   │   ├── TraceController.java     # 溯源管理
│       │   │   ├── ChainController.java     # 区块链信息
│       │   │   └── FileController.java       # 文件管理
│       │   ├── service/                      # 服务层
│       │   │   ├── PartsService.java
│       │   │   ├── TraceService.java
│       │   │   ├── ChainmakerService.java   # 长安链服务
│       │   │   └── IpfsService.java          # IPFS服务
│       │   ├── mapper/                       # 数据访问层
│       │   │   ├── PartsMapper.java
│       │   │   └── TraceMapper.java
│       │   ├── pojo/                         # 实体类
│       │   │   ├── Parts.java                # 零部件实体
│       │   │   ├── TraceRecord.java           # 溯源记录实体
│       │   │   ├── ResultResp.java           # 统一响应格式
│       │   │   └── ResultPage.java           # 分页响应格式
│       │   ├── contract/                     # 智能合约Java包装类
│       │   │   └── PartsTraceContract.java
│       │   └── utils/                        # 工具类
│       │       └── JwtUtil.java              # JWT工具
│       └── resources/
│           ├── application.yml               # 应用配置
│           ├── application-dev.yml           # 开发环境配置
│           ├── application-prod.yml          # 生产环境配置
│           ├── chainmaker/                   # 长安链配置
│           │   ├── sdk_config.yml           # SDK配置文件
│           │   └── crypto-config/            # 证书和私钥目录
│           ├── mapper/                       # MyBatis XML映射文件
│           └── static/                       # 静态资源
├── pom.xml                                    # Maven配置
└── README.md                                  # 项目说明
```

## 核心功能

### 1. 零部件管理
- 零部件注册
- 零部件信息查询
- 零部件列表分页查询
- 零部件信息更新

### 2. 溯源管理
- 添加溯源记录
- 查询零部件完整溯源链
- 溯源历史记录查询

### 3. 区块链服务
- 零部件信息上链
- 溯源记录上链
- 区块链信息查询（区块高度、节点数量、交易数量等）
- 区块和交易详情查询

### 4. 文件服务
- 文件上传到IPFS
- 文件下载
- IPFS哈希管理

## 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- 长安链节点（已部署）
- IPFS节点（可选，用于文件存储）

## 快速开始

### 1. 环境配置

设置环境变量：
```bash
export KEWEI_DBSERVER=127.0.0.1
export KEWEI_DBPORT=3306
export KEWEI_DBUSER=root
export KEWEI_DBPWD=your_password
export CHAINMAKER_CHAIN_ID=chain1
export CHAINMAKER_ORG_ID=wx-org1.chainmaker.org
```

### 2. 数据库初始化

```bash
mysql -u root -p < docs/database/init.sql
```

### 3. 长安链配置

1. 从长安链管理台下载SDK配置文件
2. 将配置文件放入 `src/main/resources/chainmaker/` 目录
3. 配置结构：
```
chainmaker/
├── sdk_config.yml          # SDK配置文件
└── crypto-config/          # 证书和私钥目录
    └── wx-org1.chainmaker.org/
        └── user/
            └── client1/
                ├── client1.sign.key
                └── client1.sign.crt
```

### 4. 编译运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/parts-trace-backend-1.0-SNAPSHOT.jar

# 或使用Maven运行
mvn spring-boot:run
```

### 5. 访问接口

- API文档: http://localhost:8080/swagger-ui.html
- 健康检查: http://localhost:8080/actuator/health

## 配置文件说明

### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: parts-trace-backend
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${KEWEI_DBSERVER:localhost}:${KEWEI_DBPORT:3306}/parts_trace?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC
    username: ${KEWEI_DBUSER:root}
    password: ${KEWEI_DBPWD:123456}

# MyBatis-Plus配置
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.kewei.parts.pojo
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# Swagger配置
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

## 开发指南

详见 [docs/development-guide.md](docs/development-guide.md)

## 部署说明

详见 [docs/deployment.md](docs/deployment.md)

## API接口文档

启动服务后访问: http://localhost:8080/swagger-ui.html

## 许可证

MIT License
