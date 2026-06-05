# hotrank-agent-loop

热度排行榜 × Agent 调度闭环 — DDD + Go 控制面 + LangGraph 大脑

## 项目结构

```
business/
  hotrank-service/   Spring Boot 3 / Java 21 — 热度上下文（物化、查询、gRPC 命令）
  contracts/         共享 proto 定义
gateway/             Go — 控制面（MCP server、interceptor 链）
agent/               Python — LangGraph 大脑（observe→decide→dispatch→verify）
deploy/              docker-compose + 部署配置
```

## 快速启动

```bash
cd deploy
docker compose up --build
```

三服务 health：
- hotrank-service: http://localhost:8080/actuator/health
- gateway: http://localhost:8081/health
- agent: http://localhost:8082/health

## 技术栈

| 层 | 技术 |
|---|---|
| 业务服务 | Java 21, Spring Boot 3.4, MyBatis, Redis, Kafka |
| 控制面 | Go 1.22, gRPC, MCP |
| 大脑 | Python 3.12, LangGraph, FastAPI |
| 基础设施 | Redis, Kafka (KRaft), H2 (阶段 1) |