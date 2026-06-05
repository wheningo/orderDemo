package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.Order;
import com.example.orderdemo.domain.order.OrderId;
import com.example.orderdemo.domain.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Read-only query operations for orders.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Optional<Order> findById(OrderId id) {
        return orderRepository.findById(id);
    }

    public Order getById(OrderId id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}