package com.example.orderdemo.domain.order;

import java.time.Instant;

public record DelayedCommand(
    String commandType,
    String targetId,
    String reason,
    String idempotencyKey,
    int delayMinutes,
    Instant scheduledAt
) {}