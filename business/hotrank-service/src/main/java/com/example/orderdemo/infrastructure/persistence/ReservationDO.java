package com.example.orderdemo.infrastructure.persistence;

import java.time.Instant;

public class ReservationDO {
    private String txKey;
    private String sku;
    private int qty;
    private String state;
    private Instant createdAt;

    public String getTxKey() { return txKey; }
    public void setTxKey(String txKey) { this.txKey = txKey; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}