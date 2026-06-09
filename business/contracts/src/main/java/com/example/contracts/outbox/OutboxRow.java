package com.example.contracts.outbox;

import java.time.Instant;

public record OutboxRow(
    Long id,
    String aggregateType,
    String aggregateId,
    String eventType,
    String payload,
    boolean published,
    Instant createdAt
) {}