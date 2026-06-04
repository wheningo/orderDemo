package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderId;

public class OrderNotFoundException extends RuntimeException {

    private final OrderId orderId;

    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + orderId.value());
        this.orderId = orderId;
    }

    public OrderId orderId() { return orderId; }
}