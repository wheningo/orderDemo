package com.example.orderdemo.infrastructure.persistence;

public class InventoryDO {
    private String sku;
    private long total;
    private long reserved;
    private long version;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getReserved() { return reserved; }
    public void setReserved(long reserved) { this.reserved = reserved; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}