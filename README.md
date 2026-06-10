# hotrank-agent-loop

热度排行榜 × AI Agent 调度闭环 — DDD 聚合守卫 + Go 控制面 + LangGraph 大脑

> **核心论点**：Agent 可以提议——做什么、什么时候做——但能不能做、到点有没有人做、做的那一刻合不合法，是系统和领域的墙说了算。

全部代码开源，配套三篇掘金技术文章（见 `docs/articles/`）。

## 项目结构

```
business/
  hotrank-service/   Spring Boot 3.5 / Java 21 — 核心业务（订单、库存、热度排行）
  contracts/         共享命令 / CommandResult / 领域事件契约
gateway/             Go — MCP 控制面（Redis 限流、幂等键、审计）
agent/               Python — LangGraph Agent（热度 + 库存两条 graph）
deploy/              docker-compose 编排
docs/articles/       技术文章（掘金发布用）
```

## 核心能力

### 订单 (Order)
- DDD 聚合根，sealed interface 状态机：`Created / Pending / Confirmed / Closed / Cancelled`
- Seata TCC 分布式事务（`@GlobalTransactional` + `@LocalTCC`）
- 幂等下单（idempotencyKey）+ 乐观锁

### 库存 (Inventory) — "那道墙"
- **防超卖不变量**：`reserve()` 内 `qty > available()` 即拒绝
- **CAS 乐观锁** + 3 次内部重试（TransactionTemplate，避免 AOP 自调用）
- **TCC 预留台账**：幂等 + 空回滚 + 防悬挂（`useTCCFence=false`，自建台账是权威）
- **两种"不行"**：`retryable=false`（永久拒绝，agent 换计划）/ `retryable=true`（CAS 冲突，原样重试）
- **50 线程并发压测**：证明 `success × qty ≤ total`（不超卖属性已实测钉死）

### 热度排行 (HotRank)
- Kafka 消费互动事件 + Redis ZSet 物化（region 分片、5min 时间桶）
- Top-K 查询（多桶 ZUNIONSTORE 合并）
- 变更检测 + HotRankChanged 事件发布
- BoostExposure 命令（weight 校验 [1,100]，领域拒绝 > 100）

### 延迟命令 (RocketMQ)
- **Agent 主动延迟**：`schedule_close_order`（4.x delayLevel，固定档位）
- **预留超时守卫**：RocketMQ 5.x `setDeliveryTimestamp`（精确毫秒，gRPC 协议）
- **Outbox 分流**：同一套 outbox，按 eventType 路由到 Kafka / RocketMQ
- **灾备 Reaper**：全表扫描降级为 5min/1h，兜底 broker 故障

### 治理栈
- **Nacos**：服务注册 + 配置中心（nacos-client 2.5.1）
- **Sentinel**：流控规则（inventory 100QPS / order 50QPS / boost 200QPS）+ 429 BlockHandler
- **Go Redis 限流**：Lua 滑动窗口，fail-open（Redis 挂了放行不阻塞）

## Agent 循环

两条 LangGraph 状态图，均支持 replan 回环（`MAX_ITERATIONS=3`）：

1. **HotRank Graph** — observe → decide（gap 大发激进 weight）→ dispatch → verify → replan（被墙→退让到合法范围）
2. **Inventory Graph** — observe → decide → dispatch → verify → replan
   - `retryable=false` → 退量到 available
   - `retryable=true` → 原样重试

MCP 工具清单（Go 控制面暴露）：

| 工具 | 功能 |
|------|------|
| `get_hot_rank` | 读取实时 Top-K |
| `dispatch_boost_exposure` | 加权曝光 |
| `allocate_promo_stock` | 库存预留 |
| `schedule_close_order` | 延迟关单 |

## 技术栈

| 层 | 技术 |
|---|---|
| 业务服务 | Java 21, Spring Boot 3.5, MyBatis, Seata TCC 2.1.0, Virtual Threads |
| 事件/消息 | Kafka (KRaft), RocketMQ 5.x (gRPC + remoting 双协议), Outbox Relay |
| 缓存 | Redis 7 |
| 治理 | Nacos 2.5, Sentinel, Spring Cloud 2024.0.1, SCA 2023.0.3.2 |
| 控制面 | Go, Redis Lua 限流, MCP Server, Interceptor Chain |
| Agent | Python 3.12+, LangGraph, FastAPI |
| 存储 | MySQL (生产) / H2 (测试/本地) |
| 协调 | Seata TC (file 模式, dev 单机) |

## 本地启动

```bash
# 1. Redis
redis-server

# 2. RocketMQ (可选，延迟命令功能需要)
# 下载 5.3.1+，修 runbroker.sh 去掉 -XX:-UseBiasedLocking
bin/mqnamesrv &
bin/mqbroker -n localhost:9876 --enable-proxy &

# 3. Seata TC (可选，分布式事务需要)
./seata-server.sh -p 8091 -m file

# 4. Java
cd business/hotrank-service
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:hotrank;MODE=MySQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.sql.init.mode=always --spring.kafka.producer.properties.max.block.ms=1000 --seata.enabled=false"

# 5. Go
cd gateway
GOPROXY=https://goproxy.cn,direct HOTRANK_SERVICE_URL=http://localhost:8080 go run .

# 6. Python
cd agent && python3 -m venv .venv && source .venv/bin/activate
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
GATEWAY_URL=http://localhost:8081 uvicorn main:app --port 8082
```

## 钱镜头（快速验证）

```bash
# 热度：agent 发 weight=236 被领域拒绝 → 退让到 100 → 成功
curl -XPOST 'localhost:8082/trigger?region=CN'

# 库存：agent 放量 300 被防超卖拒绝 → 退量到 100 → 成功
curl -XPOST 'localhost:8082/trigger/inventory?sku=SKU-1&qty=300&region=CN'

# 延迟关单
curl -XPOST localhost:8081/mcp/call -H 'Content-Type: application/json' \
  -d '{"tool":"schedule_close_order","params":{"order_id":"1","reason":"timeout","delay_minutes":5}}'
```

## DDD 分层

```
domain/          聚合根、值对象、领域事件、Repository 接口（零框架注解）
application/     应用服务、TCC Action/编排、查询服务
infrastructure/  MyBatis、Kafka/RocketMQ、Outbox、Redis 物化、Reaper
interfaces/      REST Controller、GlobalExceptionHandler
```

## 配套文章

1. [别让 AI agent 当你系统的超级管理员](docs/articles/ddd-aggregate-vs-agent-command.md) — 聚合墙 × 单命令拒绝
2. [放量下单时，库存这道墙](docs/articles/tcc-oversell-agent-convergence.md) — TCC × 并发 × 自收敛
3. [延迟命令 × RocketMQ：当 Agent 说"5 分钟后关单"](docs/articles/rocketmq-delayed-command-agent-time.md) — 时间维度 × 责任分层

## License

MIT