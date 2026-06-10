# 延迟命令 × RocketMQ：当 Agent 说"5 分钟后关单"

> agent 不只能"现在做"，还能"等一下再做"。但"等一下"要精确到毫秒、不丢、不重复、还得兜底——这比即时命令难一个数量级。

> 全部代码开源：**https://github.com/wheningo/orderDemo**
> 本篇是 Phase 2 延伸，承接前两篇。第一篇讲"聚合拒绝 agent"，第二篇讲"TCC 在并发下拒绝 agent"，这一篇讲"agent 获得时间维度的能力"。

---

## 1. 问题：agent 只有"即时命令"够用吗？

前两篇的 agent 只会"现在就做"：放量、预留库存、加权曝光。但真实业务有大量"等一下再做"的场景：

- 下单后 30 分钟未付款 → 自动关单释放库存
- 促销活动 → 定时开始/结束曝光加权
- 预留超时 → 精确到单条预留的自动释放

这些不是 cron 能解决的——它们是**每条业务实例有自己独立的倒计时**。100 个订单有 100 个不同的超时时刻，不是"每分钟扫一次全表"能精确处理的。

## 2. 两条路：delayLevel vs 精确投递

RocketMQ 提供两种延迟能力：

| | 4.x delayLevel | 5.x setDeliveryTimestamp |
|---|---|---|
| 精度 | 18 个固定档位（1s/5s/.../2h） | 任意毫秒 |
| 协议 | remoting（老 starter） | gRPC（新 client） |
| 适合 | "大约 5 分钟后" | "精确到这一单的超时时刻" |

我们两个都用——**不同场景不同精度**：

- `schedule_close_order`（agent 手动下发）→ 4.x delayLevel（"大约 5 分钟后关单"，够用）
- `StockReservationTimeoutGuard`（每条预留自动挂的超时兜底）→ 5.x 精确投递（`now + 10min` 精确到毫秒）

这不是技术洁癖——是业务需求决定的：agent 说"5 分钟后关单"不需要精确到 4min59s870ms；但"预留超时释放"如果用 delayLevel 只能选 5min 或 10min 档，而真实超时窗口可能是 7.5min。

## 3. 架构：outbox 分流 + 双协议共存

```
tryReserve 成功
  ├── outbox: StockReserved        → OutboxRelay → Kafka（下游解耦）
  └── outbox: TimeoutGuard         → OutboxRelay → RocketMQ 5.x（精确定时）
                                                    ↓
                                        配置的超时窗口后投递（默认30min）
                                                    ↓
                                        ReservationTimeoutConsumer
                                                    ↓
                                        inventoryTccService.cancel(txKey)
                                                    ↓
                                        幂等：state≠TRIED → return（已确认/已取消）
```

关键设计：**同一个 outbox + 同一个 relay，按 eventType 分流到不同的消息中间件**。不是两套 outbox，是一套出口、两条管道。

## 4. 代码：四个改造点

### 4.1 Try 阶段多写一条超时兜底（同事务原子）

```java
@Value("${inventory.reservation.timeout-minutes:30}")
private int timeoutMinutes;
private Duration reservationTimeout = Duration.ofMinutes(timeoutMinutes);

@Transactional(propagation = Propagation.REQUIRES_NEW)
public CommandResult tryReserve(String txKey, String sku, int qty) {
    // ... reserve + 台账 + StockReserved（已有）

    // 新增：超时兜底，精确到这一条预留的超时时刻
    // 超时窗口可配（application.yml），必须对齐业务支付窗口
    long deliverTimeMillis = Instant.now().plus(reservationTimeout).toEpochMilli();
    outboxWriter.write("Inventory", sku, "StockReservationTimeoutGuard",
            Map.of("txKey", txKey, "sku", sku, "qty", qty,
                   "deliverTimeMillis", deliverTimeMillis));

    return CommandResult.ok();
}
```

超时窗口**必须对齐业务真实支付窗口**——如果用户有 30 分钟付款时间，这里就配 30。写死 10 分钟意味着用户还在正常付款时预留已被释放，会造成"付款成功但库存没了"。所以这个值是 `application.yml` 可配的，不是 magic number。

### 4.2 OutboxRelay 按 eventType 分流

```java
@Scheduled(fixedDelay = 1000)
public void relay() {
    List<OutboxDO> batch = outboxMapper.findUnpublished(BATCH_SIZE);
    for (OutboxDO row : batch) {
        try {
            if ("StockReservationTimeoutGuard".equals(row.getEventType())) {
                relayToRocketMQ(row);       // 5.x gRPC 精确投递
            } else {
                relayToKafka(row);          // 普通领域事件
            }
            outboxMapper.markPublished(row.getId());
        } catch (Exception e) {
            log.warn("Relay failed id={}, will retry: {}", row.getId(), e.getMessage());
            break;
        }
    }
}

private void relayToRocketMQ(OutboxDO row) throws Exception {
    JsonNode payload = objectMapper.readTree(row.getPayload());
    String txKey = payload.get("txKey").asText();
    long deliverTimeMillis = payload.get("deliverTimeMillis").asLong();
    rocketMQ5Producer.sendScheduled(txKey, row.getPayload(), deliverTimeMillis);
}
```

### 4.3 RocketMQ 5.x Producer（精确投递）

```java
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMQ5Producer {

    private ClientServiceProvider provider;   // 字段，init() 时缓存，不在热路径重复 SPI 查找
    private Producer producer;

    @PostConstruct
    public void init() throws Exception {
        provider = ClientServiceProvider.loadService();
        ClientConfiguration config = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint)
                .build();
        producer = provider.newProducerBuilder()
                .setClientConfiguration(config)
                .setTopics("reservation-timeout-guard")
                .build();
    }

    public void sendScheduled(String key, String payload, long deliverTimeMillis) {
        Message message = provider.newMessageBuilder()   // 用缓存的 provider
                .setTopic("reservation-timeout-guard")
                .setKeys(key)
                .setBody(payload.getBytes())
                .setDeliveryTimestamp(deliverTimeMillis)   // ← 精确到毫秒
                .build();
        producer.send(message);
    }
}
```

**`setDeliveryTimestamp`** —— 这是 5.x 独有的 API。不是"delay 5 分钟"，是"在 2026-06-10T14:55:00.000Z 投递"。每条消息独立倒计时。注意 5.x client 走 gRPC 协议，broker 启动时必须 `--enable-proxy`，否则连不上。

### 4.4 消费端：精确时刻触发 cancel

```java
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class ReservationTimeoutConsumer {

    private PushConsumer consumer;

    @PostConstruct
    public void init() throws Exception {
        consumer = provider.newPushConsumerBuilder()
                .setConsumerGroup("reservation-timeout-consumer")
                .setSubscriptionExpressions(Map.of("reservation-timeout-guard", FilterExpression.SUB_ALL))
                .setMessageListener(this::onMessage)
                .build();
    }

    private ConsumeResult onMessage(MessageView message) {
        String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
        JsonNode payload = objectMapper.readTree(body);
        String txKey = payload.get("txKey").asText();

        // 直接调用现有的幂等 cancel——三种情况都安全：
        // 1. state=TRIED → 释放库存（正常超时回收）
        // 2. state=CONFIRMED → no-op（已被正常确认）
        // 3. state=CANCELLED → no-op（已被其他路径取消）
        inventoryTccService.cancel(txKey);
        return ConsumeResult.SUCCESS;
    }
}
```

注意：消费端**没有新的状态校验逻辑**——全部复用 `cancel(txKey)` 里已有的幂等判断。这就是上一篇里我们写的台账 + 状态幂等的价值：新来一个触发源（定时消息），零新增校验代码。

### 4.5 重复投递怎么办？（at-least-once + 领域幂等）

OutboxRelay 的 `sendScheduled()` 和 `markPublished()` 不是原子的——如果消息发成功、但 markPublished 前进程崩溃，下一轮 relay 会再投一条。同一个 txKey 两条超时守卫。

这**不是 bug，是有意的 at-least-once 设计**。因为：

- `cancel(txKey)` 里 `state ≠ TRIED → return`，重复消费是 no-op
- 如果消息丢了（broker 故障），重投反而是保护

也就是说：relay 保证"至少投一次"，领域幂等保证"多投不坏事"。这两层加起来 = **不丢不错**。

如果你追求"恰好一次"（exactly-once），需要事务消息或本地消息表 + 对账——复杂度翻倍，而收益是零（因为消费端已经幂等了）。所以我不做。

## 5. 对比：粗粒度扫表 vs 精确定时消息

之前的 `ReservationReaper`（全表扫描 + 30min 超时窗口）有两个问题：

- **精度差**：预留 T 时刻创建，最早 T+30min 才被发现、最迟 T+31min（扫描间隔 60s）。中间 30 分钟库存被虚占。
- **扩展差**：`findStaleTried(before)` 随预留量线性增长。

RocketMQ 定时消息：

- **精度高**：每条预留有自己的精确投递时刻（T + 配置超时窗口），broker 负责定时，不靠客户端扫描。
- **扩展好**：消息量增长由 broker 水平扩展承担，客户端只消费到达的消息。

但 **RocketMQ 不是银弹** —— broker 故障、消息丢失是可能的。所以 ReservationReaper 不删，**降级为灾备**：

```java
/**
 * Disaster-backup reaper: catches reservations that RocketMQ timeout guard missed.
 * Primary mechanism: per-reservation RocketMQ scheduled message.
 * This is the safety net when broker is down or messages are lost.
 */
@Scheduled(fixedDelay = 300_000)   // 从 60s → 5min（不再是主力）
public void reapStale() {
    Instant before = Instant.now().minus(Duration.ofHours(1));   // 从 30min → 1h（灾备兜底）
    // ...
}
```

## 6. Agent 的延迟命令（另一条路径）

除了超时兜底（被动），agent 还能主动下发延迟命令：

```bash
# agent 说"5 分钟后关单"
curl -XPOST localhost:8081/mcp/call -H 'Content-Type: application/json' \
  -d '{"tool":"schedule_close_order","params":{"order_id":"123","reason":"timeout","delay_minutes":5}}'
```

这条走的是 4.x delayLevel（`rocketmq-spring-boot-starter`），用固定档位够了。Go 控制面照常加幂等键 + 审计：

```
agent → Go (idempotency + rate limit + audit)
     → Java POST /schedule/close-order
     → RocketMQ 4.x delayed message (level 9 = 5min)
     → consumer → Order.close()
```

两条路径、两种精度、两种协议——但**消费端的幂等逻辑完全复用**。

## 7. 放弃了什么

- **没用单一协议统一两种延迟**：可以全用 5.x，但 `schedule_close_order` 的 "大约 5 分钟" 场景用老 starter 更简单（不需要管理 5.x Producer 生命周期），且已经跑通了。两套共存的代价是两个 client 依赖，但各走各的、互不干扰。
- **没用 @RocketMQTransactionListener 做事务消息**：outbox 已经保证了"业务落地 = 消息一定会发"。再加事务消息是重复保证，复杂度白涨。
- **Reaper 没删**：代码多留一个定时任务。但它是最后一道安全网——系统的正确性不应该只依赖一个外部中间件的可用性。

## 8. 要点

| 场景 | 机制 | 精度 | 协议 |
|------|------|------|------|
| agent 主动延迟命令 | DelayedCommandProducer | 固定 18 档 | 4.x remoting |
| 预留超时自动释放 | RocketMQ5Producer | 精确毫秒 | 5.x gRPC |
| broker 故障兜底 | ReservationReaper | 1h 粗扫 | 无（本地定时） |

三层不是冗余——它们的精度、触发方式、故障假设都不同：
- 定时消息假设 broker 可用 → 主力
- Reaper 假设 broker 可能挂 → 兜底
- 两者都不假设对方存在 → 互相独立、幂等收敛

## 9. 复现

```bash
git clone https://github.com/wheningo/orderDemo.git
cd orderDemo

# 1. RocketMQ 5.x（5.3.1+ 均可；Java 21 需要去掉 UseBiasedLocking）
#    5.x client 走 gRPC，broker 必须 --enable-proxy
wget https://dlcdn.apache.org/rocketmq/5.3.1/rocketmq-all-5.3.1-bin-release.zip
unzip rocketmq-all-5.3.1-bin-release.zip
cd rocketmq-all-5.3.1-bin-release
# 修 bin/runbroker.sh：删除 -XX:-UseBiasedLocking（Java 18+ 已移除该选项）
sed -i 's/-XX:-UseBiasedLocking//' bin/runbroker.sh
bin/mqnamesrv &
sleep 5
bin/mqbroker -n localhost:9876 --enable-proxy &   # --enable-proxy 是 5.x gRPC client 必需的

# 2. Redis
redis-server &

# 3. Java
cd business/hotrank-service
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:hotrank;MODE=MySQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.sql.init.mode=always --spring.kafka.producer.properties.max.block.ms=1000 --seata.enabled=false"

# 4. Go
cd gateway && GOPROXY=https://goproxy.cn,direct HOTRANK_SERVICE_URL=http://localhost:8080 go run .

# 5. 验证延迟关单
# 先下单
curl -XPOST localhost:8080/orders/place -H 'Content-Type: application/json' \
  -d '{"productName":"Coffee","quantity":1,"sku":"SKU-1"}'
# 记下 orderId，然后 schedule 关单
curl -XPOST localhost:8081/mcp/call -H 'Content-Type: application/json' \
  -d '{"tool":"schedule_close_order","params":{"order_id":"1","reason":"timeout","delay_minutes":1}}'
# 等 1 分钟后查询
sleep 65 && curl localhost:8080/orders/1
# 期望：state = "CLOSED"
```

---

**下一篇预告**：风控层落地 —— 当 `riskTier` 不再只是透传字段，L3/L4 命令怎么强制走人工审批。