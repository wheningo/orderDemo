package com.example.contracts.command;

public record AllocatePromoStockCmd(
    String sku,
    int qty,
    String region,
    String idempotencyKey,
    String riskTier
) {
    public AllocatePromoStockCmd {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got: " + qty);
        }
    }
}