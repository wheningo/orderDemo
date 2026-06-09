package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderId;

public class OrderIdHolder {

    private static final ThreadLocal<OrderId> HOLDER = new ThreadLocal<>();

    public static void set(OrderId orderId) {
        HOLDER.set(orderId);
    }

    public static OrderId get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}