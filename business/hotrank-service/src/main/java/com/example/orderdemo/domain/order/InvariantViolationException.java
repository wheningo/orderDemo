package com.example.orderdemo.domain.order;

public class InvariantViolationException extends RuntimeException {
    private final OrderId orderId;
    private final String invariant;

    public InvariantViolationException(OrderId orderId, String invariant, String message) {
        super(message);
        this.orderId = orderId;
        this.invariant = invariant;
    }

    public OrderId orderId() { return orderId; }
    public String invariant() { return invariant; }
}