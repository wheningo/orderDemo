package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.domain.order.OrderId;
import com.example.orderdemo.domain.order.OrderState;

/**
 * Read model returned from REST endpoints. Decoupled from the domain aggregate.
 */
public record OrderResponse(
        Long id,
        String productName,
        int quantity,
        String state
) {
    public static OrderResponse from(com.example.orderdemo.domain.order.Order order) {
        Long idValue = order.id() != null ? order.id().value() : null;
        return new OrderResponse(idValue, order.productName(), order.quantity(),
                order.state().description());
    }
}