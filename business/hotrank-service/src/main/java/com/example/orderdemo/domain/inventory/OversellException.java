package com.example.orderdemo.domain.inventory;

public class OversellException extends RuntimeException {
    private final String sku;
    private final int requested;
    private final long available;

    public OversellException(String sku, int requested, long available) {
        super("Oversell rejected: sku=%s, requested=%d, available=%d".formatted(sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String sku() { return sku; }
    public int requested() { return requested; }
    public long available() { return available; }
}