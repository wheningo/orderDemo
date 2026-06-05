package com.example.orderdemo.domain.order;

public sealed interface OrderCommand {

    record PlaceOrder(String productName, int quantity, String idempotencyKey) implements OrderCommand {
        public PlaceOrder {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
            }
        }
    }

    record ConfirmOrder(OrderId orderId, String idempotencyKey) implements OrderCommand {}

    record CloseOrder(OrderId orderId, String reason, String idempotencyKey) implements OrderCommand {}

    record CancelOrder(OrderId orderId, String reason, String idempotencyKey) implements OrderCommand {}
}

