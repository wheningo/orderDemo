package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderId;

public record PlaceOrderResult(boolean success, OrderId orderId, String txKey, String reason) {

    public static PlaceOrderResult success(OrderId orderId, String txKey) {
        return new PlaceOrderResult(true, orderId, txKey, null);
    }

    public static PlaceOrderResult failed(String reason) {
        return new PlaceOrderResult(false, null, null, reason);
    }
}