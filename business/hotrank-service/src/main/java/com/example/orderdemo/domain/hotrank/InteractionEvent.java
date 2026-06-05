package com.example.orderdemo.domain.hotrank;

import java.time.Instant;

public record InteractionEvent(
    String eventId,
    String contentId,
    String region,
    String interactionType,  // LIKE, GIFT, COMMENT, SHARE
    int weight,
    Instant occurredAt
) {}