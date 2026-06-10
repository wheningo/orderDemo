package com.example.contracts.event;

public record StockReservationTimeoutGuard(String txKey, String sku, int qty, long deliverTimeMillis) {}