# Order Demo - 高并发分布式订单微服务

这是一个展示高并发和分布式事务能力的订单微服务 demo，使用 **Spring Boot 3** 和 **Spring Cloud Alibaba** 构建，集成了多种现代技术栈，适用于大厂技术面试展示。

## 项目简介
- **目标**: 实现订单创建、库存扣减的分布式事务，支持高并发场景，日订单处理能力达 10 万。
- **技术栈**:
    - **Spring Boot 3**: 基础框架。
    - **Spring Cloud Alibaba**:
        - **Nacos**: 服务注册与配置管理。
        - **Seata TCC**: 分布式事务（Try-Confirm-Cancel 模式）。
    - **MySQL**: 数据存储，HikariCP 连接池优化。
    - **Redis**: 分布式锁和缓存（Redisson）。
    - **Kafka**: 异步事件处理。
    - **Spring Boot Actuator**: 系统监控，OAuth2（JWT）保护。
    - **Java 17**: 密封类（Sealed Class）管理订单状态。

## 功能亮点
1. **分布式事务**:
    - 使用 Seata TCC 模式，手写 Try-Confirm-Cancel 逻辑，确保订单和库存一致性。
    - 支持幂等性、超时和重试。
2. **高并发优化**:
    - Redis 分布式锁（Redisson）防止超卖。
    - HikariCP 连接池配置，最大 20 连接，30 秒空闲回收。
    - 异步处理（@Async）和 Kafka 事件驱动。
3. **安全性**:
    - Actuator 端点通过 OAuth2（Keycloak）保护，使用 JWT 认证。
4. **监控**:
    - Actuator 暴露 HikariCP、JVM 和 HTTP 请求指标，支持 Prometheus 格式。

## 环境要求
- **Java**: 17
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **Kafka**: 3.0+
- **Nacos**: 2.3.x
- **Seata**: 2.1.0
- **Keycloak**: 23.0.6（OAuth2 授权服务器）

## 快速开始
### 1. 安装依赖
```bash
# MySQL
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=your_password mysql:8.0

# Redis
docker run -d -p 6379:6379 redis

# Kafka
docker run -d -p 9092:9092 apache/kafka:latest

# Nacos
wget https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.tar.gz
tar -xvf nacos-server-2.3.2.tar.gz
cd nacos/bin
sh startup.sh -m standalone

# Seata
wget https://github.com/seata/seata/releases/download/v2.1.0/seata-server-2.1.0.tar.gz
tar -xvf seata-server-2.1.0.tar.gz
cd seata/bin
sh seata-server.sh

# Keycloak
docker run -d -p 8082:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin123 quay.io/keycloak/keycloak:23.0.6