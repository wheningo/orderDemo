package com.example.contracts.event;

import java.time.Instant;

public record StockReserved(String eventId, String sku, int qty, String txKey, Instant occurredAt) {}