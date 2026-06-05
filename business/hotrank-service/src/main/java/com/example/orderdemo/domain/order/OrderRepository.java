package com.example.orderdemo.domain.order;

import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    boolean existsByIdempotencyKey(String idempotencyKey);
}