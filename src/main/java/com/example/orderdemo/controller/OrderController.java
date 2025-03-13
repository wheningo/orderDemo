package com.example.orderdemo.controller;

import com.example.orderdemo.model.Order;
import com.example.orderdemo.model.Created;
import com.example.orderdemo.model.Confirmed;
import com.example.orderdemo.model.Cancelled;
import com.example.orderdemo.model.OrderState;
import com.example.orderdemo.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * @author whening
 * @description
 * @date 2025/3/12 17:25
 * @since 1.0
 **/
@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired private OrderService orderService;

    @PostMapping
    public CompletableFuture<ResponseEntity<Order>> createOrder(
            @RequestParam String productName, @RequestParam int quantity) {
        logger.info("Received create order request: product={}, quantity={}", productName, quantity);
        return orderService.createOrder(productName, quantity)
                .thenApply(order -> {
                    logger.info("Order creation completed: id={}", order.id());
                    return ResponseEntity.ok(order);
                });
    }

    @PutMapping("/{id}/state")
    public ResponseEntity<Order> updateOrderState(@PathVariable Long id, @RequestParam String state) {
        logger.info("Received update state request: id={}, state={}", id, state);
        OrderState newState = switch (state.toUpperCase()) {
            case "CREATED" -> new Created();
            case "CONFIRMED" -> new Confirmed();
            case "CANCELLED" -> new Cancelled();
            default -> throw new IllegalArgumentException("Invalid state: " + state);
        };
        Order updatedOrder = orderService.updateState(id, newState);
        logger.info("Order state update completed: id={}", id);
        return ResponseEntity.ok(updatedOrder);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        logger.info("Received get order request: id={}", id);
        return orderService.findById(id)
                .map(order -> {
                    logger.info("Order retrieved: id={}", id);
                    return ResponseEntity.ok(order);
                })
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }
}
