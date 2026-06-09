package com.example.contracts.event;

import java.time.Instant;

public record OrderConfirmed(String eventId, String orderId, Instant occurredAt) {}