package com.example.orderdemo.controller;

import com.example.orderdemo.model.Order;
import com.example.orderdemo.model.OrderState;
import com.example.orderdemo.model.Paid;
import com.example.orderdemo.model.Shipped;
import com.example.orderdemo.repository.OrderRepository;
import com.example.orderdemo.service.OrderService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
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
    @Autowired
    private OrderService orderService;



    @PostMapping
    public CompletableFuture<ResponseEntity<Order>> createOrder(
            @RequestParam String productName, @RequestParam int quantity) {
        return orderService.createOrder(productName, quantity)
                .thenApply(order -> ResponseEntity.ok(order));
    }

    @PutMapping("/{id}/state")
    public ResponseEntity<Order> updateOrderState(@PathVariable Long id, @RequestParam String state) {
        OrderState newState = switch (state) {
            case "paid" -> new Paid();
            case "shipped" -> new Shipped();
            default -> throw new IllegalArgumentException("Invalid state: " + state);
        };
        Order updatedOrder = orderService.updateState(id, newState);
        return ResponseEntity.ok(updatedOrder);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }
}
