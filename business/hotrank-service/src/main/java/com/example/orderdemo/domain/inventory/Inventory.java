package com.example.orderdemo.domain.inventory;

public class Inventory {

    private String sku;
    private long total;
    private long reserved;
    private long version;

    private Inventory() {}

    public static Inventory create(String sku, long total) {
        var inv = new Inventory();
        inv.sku = sku;
        inv.total = total;
        inv.reserved = 0;
        inv.version = 0;
        return inv;
    }

    public static Inventory reconstitute(String sku, long total, long reserved, long version) {
        var inv = new Inventory();
        inv.sku = sku;
        inv.total = total;
        inv.reserved = reserved;
        inv.version = version;
        return inv;
    }

    public long available() {
        return total - reserved;
    }

    public void reserve(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        if (qty > available()) {
            throw new OversellException(sku, qty, available());
        }
        this.reserved += qty;
    }

    public void confirm(int qty) {
        if (qty > reserved) throw new IllegalStateException("Cannot confirm more than reserved");
        this.reserved -= qty;
        this.total -= qty;
    }

    public void cancel(int qty) {
        if (qty > reserved) throw new IllegalStateException("Cannot cancel more than reserved");
        this.reserved -= qty;
    }

    public String sku() { return sku; }
    public long total() { return total; }
    public long reserved() { return reserved; }
    public long version() { return version; }
}