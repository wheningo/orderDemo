package com.example.orderdemo.domain.order;

import com.example.orderdemo.domain.shared.DomainEvent;
import java.time.Instant;

public sealed interface OrderEvent extends DomainEvent {

    record OrderCreated(OrderId orderId, String productName, int quantity, Instant occurredAt)
            implements OrderEvent {}

    record OrderConfirmed(OrderId orderId, Instant occurredAt)
            implements OrderEvent {}

    record OrderClosed(OrderId orderId, String reason, Instant occurredAt)
            implements OrderEvent {}

    record OrderCancelled(OrderId orderId, String reason, Instant occurredAt)
            implements OrderEvent {}
}