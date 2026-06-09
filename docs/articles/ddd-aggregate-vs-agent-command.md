# DDD 聚合 × Agent 命令：那道拒绝 AI 的墙

> 当 AI agent 想给热度榜"加把力"时，它发了 weight=236——领域说：不行。agent 退让到 100，领域放行。这就是受控失败。

## 问题：谁来管住 agent？

AI agent 越来越能干，但"能干"和"能信"之间差一道墙。

考虑一个热度排行榜场景：agent 观察当前排名，决定给低排位内容加权曝光。问题来了——如果 agent 决定加权 236 分（合法范围是 1-100），谁拦它？

**不是 prompt。** Prompt 是建议，不是约束。
**不是控制面。** Go interceptor 管限流和审计，但不管业务规则。
**是领域本身。** DDD 聚合根的不变量——这是代码级的、编译器可检查的、绕不过去的墙。

## 架构：三层各守一道

```
Python (LangGraph)     →  observe/decide/dispatch/verify（含 replan 回环）
Go (Control Plane)     →  幂等键注入 / rate-limit / audit
Java (Domain)          →  不变量守卫 + 命令去重，最后一道墙
```

agent 的命令必须穿过 Go 拦截器链，再打到 Java 领域层。任何一层都可以拒绝，但只有领域知道"236 不合法"。

## 代码：墙长什么样

Java 21 的 record 做命令，compact constructor 里就是不变量：

```java
public record BoostExposureCommand(
    String targetContentId,
    int weight,
    String region,
    String idempotencyKey,
    String decisionSource,
    String riskTier   // 由控制面注入，领域可据此做分级策略
) {
    public BoostExposureCommand {
        if (weight < 1 || weight > 100) {
            throw new IllegalArgumentException(
                "Weight must be between 1 and 100, got: " + weight);
        }
    }
}
```

这不是 validation annotation，不是 if-else 在 controller 里。这是**领域对象的构造契约**——你拿不到一个非法的命令实例。`riskTier` 是控制面带过来的元数据（agent 激进决策时标 "high"），领域可以用它做更细的准入策略——但 Phase 1 先只卡 weight 范围。（`riskTier` 的完整故事：命令分级 L1–L4，查询/推荐/自动执行/人工审批，agent 不能自我提权——高风险命令会被强制走人工审批。留到后续篇章展开。）

Go 控制面的拦截器链负责限流和审计，但不做业务校验——职责分明：

```go
// 拦截器链：生成幂等键 → 限流 → (转发到 Java)
chain := interceptor.NewChain(
    interceptor.NewIdempotencyInterceptor(),  // 生成/校验幂等键格式，不去重
    interceptor.NewRateLimitInterceptor(10, 20),
)

// 审计包在链外——无论通过还是被拦，每条命令都有记录
audit.Record(ctx, "REJECTED_BY_DOMAIN:Weight must be between 1 and 100, got: 236")
```

注意：Go 的 `IdempotencyInterceptor` **只负责生成和校验幂等键格式**，不做去重判断。真正的命令去重在 Java 侧（Redis `SETNX`）——因为只有持久化层才能保证"这条命令是否真的执行过"。

Python agent 的 decide 节点用规则策略，当排名差距过大时会算出一个激进权重：

```python
def decide(state: AgentState) -> AgentState:
    top_k = state.get("top_k", [])
    if not top_k:
        return {"decision": None}

    target = top_k[-1]
    gap = top_k[0].get("score", 0) - target.get("score", 0)

    # 第一次：差距大就发力——很可能越过领域上限，撞墙
    if gap > 200:
        weight = int(gap * 0.8)            # 295 * 0.8 = 236 > 100
    else:
        weight = 10

    # 重规划：上一条被领域拒了，退到合法上限再试
    if state.get("replan"):
        weight = min(weight, 100)

    return {"decision": {"target": target, "weight": weight}}
```

## Demo：钱镜头

灌入种子数据（hot-1: 300 分，cold-1: 5 分），触发 agent：

```bash
curl -XPOST 'localhost:8082/trigger?region=CN' | python3 -m json.tool
```

返回（注意 `attempts`——一次完整的"撞墙→退让→成功"）：

```json
{
  "decision": {"target": {"contentId": "cold-1", "score": 5}, "weight": 100},
  "attempts": [
    {"weight": 236, "accepted": false, "reason": "Weight must be between 1 and 100, got: 236"},
    {"weight": 100, "accepted": true,  "reason": ""}
  ],
  "effect": {"applied": true, "target": "cold-1", "new_score": 105},
  "iterations": 2,
  "replan": false
}
```

完整链路：

1. **observe** — agent 读到 `[hot-1:300, cold-1:5]`
2. **decide** — gap=295 > 200，激进算出 weight=236
3. **dispatch** — Go 盖幂等键 → 过限流 → 转发 Java
4. **Java 领域** — `BoostExposureCommand(236)` 撞不变量，结构化返回 `{accepted:false}`；Go 审计 `REJECTED_BY_DOMAIN`
5. **verify** — 命令被拒 → `replan=true` → 条件边回到 **decide**
6. **decide（重规划）** — 收到"被墙"信号，退到合法上限 weight=100
7. **dispatch → Java** — 这次接受，cold-1 +100 → 105
8. **verify** — `accepted=true`，`replan=false` → 结束

**这才是"受控失败"的完整含义。**

注意第 4 到第 7 步：agent 第一次发力过猛，被领域一句"got: 236"顶回来。但它没崩溃、也没死循环重发同一条——verify 把"被墙"标成 `replan`，图按条件边回到 `decide`，`decide` 收到信号后退到合法上限 100 再发一次，这次落地了（并设了 `MAX_ITERATIONS=3` 上限，真拒到死也不会无限重试）。

一次概率系统的过激决策，被领域**挡下、反馈、并在下一轮自我收敛成一个合法动作**。墙不只是拦截器，它构成了一个让 agent 自我修正的闭环——这正是让一个概率系统**敢于**参与一个不能出错的系统的前提。

## 为什么不在 Go 层校验 weight？

可以加，但那是**防御纵深**，不是**权威**。这里我**故意没在 Go 重复一份 weight 校验**。重复=两份规则迟早漂移。代价是：一条明显非法的命令也要白跑一趟网络到 Java 才被拒。我认这个开销——换"规则只有一处、永不漂移"。这是一个有意识的取舍，不是疏漏。

- Java 校验是**真理来源**——即使 Go 被绕过（新入口、内网直连）、即使规则改了（范围从 100 调到 50），领域照拒
- 规则集中在一处——不用 Go 和 Java 同步维护两份 weight 校验逻辑
- sealed interface 让编译器保证你不会忘记处理新状态

```java
public sealed interface OrderState {
    record Created() implements OrderState { ... }
    record Confirmed() implements OrderState { ... }
    record Closed() implements OrderState { ... }
    record Cancelled() implements OrderState { ... }

    // switch 不写 default，加新状态时编译器报错
}
```

如果你的场景对延迟极度敏感（每个请求省一次 Java round-trip 有价值），在 Go 加一层 pre-check 没问题——但它只是快速 reject 的优化，不是规则的权威来源。

## 要点

| 层 | 职责 | 拒绝什么 |
|---|---|---|
| Python agent | 策略决策 | 不拒绝——它发命令，也**接收拒绝信号并退让重试** |
| Go 控制面 | 幂等键注入 / 限流 / 全量审计 | **超速（限流）**。注：只保证每条命令带上幂等键，**不做去重** |
| Java 领域 | 业务不变量 + 命令去重（SETNX） | 越界参数、非法状态转换、**重复命令** |

三层不是冗余——它们拒绝的东西不同：agent 发得太快，Go 用限流挡；agent 发得太猛或发重复，Java 用不变量和幂等去重挡。Go 只负责盖幂等键、限速、留痕，**真正的业务级拒绝（包括去重）都在领域**——这也是为什么"权威"在 Java，不在 Go。

## 复现

```bash
git clone git@github.com:wheningo/orderDemo.git
cd orderDemo

# 终端1: Redis
redis-server

# 终端2: Java —— 加 max.block.ms，没 broker 时让发布快速失败（否则会卡 ~60s）
cd business/hotrank-service
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:hotrank;MODE=MySQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.sql.init.mode=always --spring.kafka.producer.properties.max.block.ms=1000"

# 终端3: Go —— 墙内必须设 GOPROXY，否则拉依赖超时
cd gateway
GOPROXY=https://goproxy.cn,direct HOTRANK_SERVICE_URL=http://localhost:8080 go run .

# 终端4: Python（pip 走清华源提速）
cd agent && python3 -m venv .venv && source .venv/bin/activate
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
GATEWAY_URL=http://localhost:8081 uvicorn main:app --port 8082

# 终端5: 灌种子 + 触发钱镜头
# 给 hot-1 刷 300 分（3 次 × 100）
for i in 1 2 3; do
  curl -s -XPOST localhost:8081/mcp/call -H 'Content-Type: application/json' \
    -d '{"tool":"dispatch_boost_exposure","params":{"target_content_id":"hot-1","weight":100,"region":"CN","decision_source":"seed","risk_tier":"standard"}}'; echo
done
# 给 cold-1 刷 5 分
curl -s -XPOST localhost:8081/mcp/call -H 'Content-Type: application/json' \
  -d '{"tool":"dispatch_boost_exposure","params":{"target_content_id":"cold-1","weight":5,"region":"CN","decision_source":"seed","risk_tier":"standard"}}'; echo

# 触发 agent —— 看到 weight=236 被拒绝 → 退让到 100 → 成功
curl -XPOST 'localhost:8082/trigger?region=CN' | python3 -m json.tool
```

> 注：本 demo **不需要启动 Kafka broker**。没有 broker 时，事件发布会快速失败（已被 `max.block.ms` 限制），不影响"撞墙"链路——它发生在命令校验阶段，根本走不到事件发布。

---

**下一篇预告**：《分布式事务 + Agent 调度：延迟队列怎么变成调度器》——当 agent 说"5 分钟后关单"，Seata + RocketMQ 延迟消息怎么接。