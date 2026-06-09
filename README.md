# OrderDemo — DDD + Agent 调度闭环

订单/库存/热度排行 × LangGraph Agent 闭环演示项目。采用 DDD 分层架构，Go 网关作为 MCP 控制面，Python Agent 实现 observe→decide→dispatch→verify 自主循环。

## 项目结构

```
business/
  hotrank-service/   Spring Boot 3.5 / Java 21 — 核心业务服务（订单、库存、热度排行）
  contracts/         共享 proto / 契约定义
gateway/             Go — MCP 控制面（interceptor 链: 幂等、限流、审计）
agent/               Python — LangGraph Agent（热度排行 + 库存分配两条 graph）
deploy/              docker-compose 一键编排
```

## 核心领域

### 订单 (Order)

DDD 聚合根，状态机驱动：

```
Created → Confirmed / Closed / Cancelled
Pending → Confirmed / Cancelled (TCC 场景)
```

- 幂等下单（idempotencyKey 去重）
- 领域事件发布（OrderCreated / Confirmed / Closed / Cancelled）
- 乐观锁版本控制

### 库存 (Inventory)

防超卖核心链路：

- CAS 乐观锁 + 重试
- Seata TCC 分布式事务（Try 预留 → Confirm 扣减 → Cancel 释放）
- Reservation 预留台账 + ReservationReaper 过期清理
- Outbox 可靠事件投递

### 热度排行 (HotRank)

- Kafka 消费互动事件（InteractionEvent）
- Redis 物化排行榜
- 变更检测 + 领域事件发布
- Agent 调用 BoostExposure 提权

## Agent 循环

两条 LangGraph 状态图：

1. **HotRank Graph** — observe(查询排行) → decide(选择提权目标) → dispatch(调用 MCP) → verify(确认结果)
2. **Inventory Graph** — observe → decide(确定分配量) → dispatch(调 allocatePromoStock) → verify → 可重入 replan（超卖退量/CAS 冲突重试，最多 3 轮）

## 技术栈

| 层 | 技术 |
|---|---|
| 业务服务 | Java 21, Spring Boot 3.5, MyBatis, Seata TCC, Virtual Threads |
| 事件 / 消息 | Kafka (KRaft), Outbox Relay |
| 缓存 | Redis 7 |
| 控制面 | Go 1.25, MCP Server, Interceptor Chain |
| Agent | Python 3.12, LangGraph, FastAPI |
| 存储 | MySQL (本地) / H2 (Docker 容器内) |

## 快速启动

```bash
cd deploy
docker compose up --build
```

三服务健康检查：

| 服务 | 地址 |
|---|---|
| hotrank-service | http://localhost:8080/actuator/health |
| gateway | http://localhost:8081/health |
| agent | http://localhost:8082/health |

## API 入口

```bash
# 下单
POST http://localhost:8080/orders/place

# 库存 Agent 分配（oversell→backoff→succeed）
POST http://localhost:8082/trigger/inventory?sku=SKU-1&qty=300

# 热度排行 Agent 循环
POST http://localhost:8082/trigger?region=CN

# MCP 网关
POST http://localhost:8081/mcp/
```

## DDD 分层

```
domain/          聚合根、值对象、领域事件、Repository 接口
application/     应用服务、TCC 编排、查询服务
infrastructure/  MyBatis 实现、Kafka 发布/消费、Outbox、Redis 物化
interfaces/      REST Controller
```