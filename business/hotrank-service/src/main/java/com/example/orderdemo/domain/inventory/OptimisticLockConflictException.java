package com.example.orderdemo.domain.inventory;

public class OptimisticLockConflictException extends RuntimeException {
    private final String sku;

    public OptimisticLockConflictException(String sku) {
        super("Optimistic lock conflict for sku: " + sku);
        this.sku = sku;
    }

    public String sku() { return sku; }
}