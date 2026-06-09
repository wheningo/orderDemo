package com.example.orderdemo.domain.inventory;

import java.time.Instant;

public class Reservation {

    public enum State { TRIED, CONFIRMED, CANCELLED }

    private String txKey;
    private String sku;
    private int qty;
    private State state;
    private Instant createdAt;

    private Reservation() {}

    public static Reservation create(String txKey, String sku, int qty) {
        var r = new Reservation();
        r.txKey = txKey;
        r.sku = sku;
        r.qty = qty;
        r.state = State.TRIED;
        r.createdAt = Instant.now();
        return r;
    }

    public static Reservation emptyRollback(String txKey, String sku) {
        var r = new Reservation();
        r.txKey = txKey;
        r.sku = sku;
        r.qty = 0;
        r.state = State.CANCELLED;
        r.createdAt = Instant.now();
        return r;
    }

    public void confirm() {
        if (this.state != State.TRIED) return;
        this.state = State.CONFIRMED;
    }

    public void cancel() {
        if (this.state != State.TRIED) return;
        this.state = State.CANCELLED;
    }

    public String txKey() { return txKey; }
    public String sku() { return sku; }
    public int qty() { return qty; }
    public State state() { return state; }
    public Instant createdAt() { return createdAt; }
}