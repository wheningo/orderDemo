package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTccService {

    private static final Logger log = LoggerFactory.getLogger(OrderTccService.class);

    private final OrderRepository orderRepository;

    public OrderTccService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderId tryCreate(String txKey, String productName, int quantity) {
        var cmd = new OrderCommand.PlaceOrder(productName, quantity, txKey);
        var order = Order.createPending(cmd);
        orderRepository.save(order);
        log.info("TCC Try: order created PENDING, id={}, txKey={}", order.id().value(), txKey);
        return order.id();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(OrderId orderId) {
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("TCC Confirm: order not found, id={}", orderId.value());
            return;
        }
        if (!(order.state() instanceof OrderState.Pending)) {
            log.info("TCC Confirm: order not in PENDING, id={}, state={}", orderId.value(), order.state().description());
            return;
        }
        order.confirmFromPending();
        orderRepository.save(order);
        log.info("TCC Confirm: order confirmed, id={}", orderId.value());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(OrderId orderId) {
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("TCC Cancel: order not found, id={}", orderId.value());
            return;
        }
        if (!(order.state() instanceof OrderState.Pending)) {
            log.info("TCC Cancel: order not in PENDING, id={}, state={}", orderId.value(), order.state().description());
            return;
        }
        order.cancelFromPending();
        orderRepository.save(order);
        log.info("TCC Cancel: order cancelled, id={}", orderId.value());
    }
}