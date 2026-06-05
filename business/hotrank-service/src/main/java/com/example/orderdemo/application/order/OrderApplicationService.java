package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates order commands: idempotency check → domain mutation → persist → publish events.
 */
@Service
@Transactional
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderApplicationService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public OrderId placeOrder(OrderCommand.PlaceOrder cmd) {
        if (orderRepository.existsByIdempotencyKey(cmd.idempotencyKey())) {
            log.info("Duplicate PlaceOrder ignored, idempotencyKey={}", cmd.idempotencyKey());
            // Idempotent: return without creating a duplicate.
            // Caller re-queries by idempotency key if it needs the id.
            throw new DuplicateCommandException(cmd.idempotencyKey());
        }

        var order = Order.create(cmd);
        orderRepository.save(order);
        publishAndClear(order);

        log.info("Order placed: id={}, product={}", order.id().value(), cmd.productName());
        return order.id();
    }

    public void confirmOrder(OrderCommand.ConfirmOrder cmd) {
        var order = loadOrder(cmd.orderId());
        order.confirm(cmd);
        orderRepository.save(order);
        publishAndClear(order);
        log.info("Order confirmed: id={}", cmd.orderId().value());
    }

    public void closeOrder(OrderCommand.CloseOrder cmd) {
        var order = loadOrder(cmd.orderId());
        order.close(cmd);
        orderRepository.save(order);
        publishAndClear(order);
        log.info("Order closed: id={}, reason={}", cmd.orderId().value(), cmd.reason());
    }

    public void cancelOrder(OrderCommand.CancelOrder cmd) {
        var order = loadOrder(cmd.orderId());
        order.cancel(cmd);
        orderRepository.save(order);
        publishAndClear(order);
        log.info("Order cancelled: id={}, reason={}", cmd.orderId().value(), cmd.reason());
    }

    // --- helpers ---

    private Order loadOrder(OrderId id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void publishAndClear(Order order) {
        order.domainEvents().forEach(eventPublisher::publish);
        order.clearEvents();
    }
}