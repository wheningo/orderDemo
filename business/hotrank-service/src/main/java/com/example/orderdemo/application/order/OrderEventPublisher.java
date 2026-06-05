package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderEvent;

/**
 * Port interface — implemented in the infrastructure layer (Task 7).
 */
public interface OrderEventPublisher {
    void publish(OrderEvent event);
}