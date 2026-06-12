package com.example.riskcontrol.domain;

public record RiskEvaluationRequest(
    String commandType,
    String targetId,
    String region,
    String agentId,
    String riskTier,
    int amount,
    int frequencyLastMinute,
    String idempotencyKey
) {}