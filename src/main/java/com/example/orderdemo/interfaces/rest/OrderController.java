package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.*;
import com.example.orderdemo.domain.order.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService applicationService;
    private final OrderQueryService queryService;

    public OrderController(OrderApplicationService applicationService,
                           OrderQueryService queryService) {
        this.applicationService = applicationService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestParam String productName,
            @RequestParam int quantity,
            @RequestParam String idempotencyKey) {

        var cmd = new OrderCommand.PlaceOrder(productName, quantity, idempotencyKey);
        OrderId id = applicationService.placeOrder(cmd);
        var order = queryService.getById(id);
        return ResponseEntity
                .created(URI.create("/orders/" + id.value()))
                .body(OrderResponse.from(order));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(
            @PathVariable Long id,
            @RequestParam String idempotencyKey) {

        var orderId = new OrderId(id);
        applicationService.confirmOrder(new OrderCommand.ConfirmOrder(orderId, idempotencyKey));
        return ResponseEntity.ok(OrderResponse.from(queryService.getById(orderId)));
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<OrderResponse> closeOrder(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam String idempotencyKey) {

        var orderId = new OrderId(id);
        applicationService.closeOrder(new OrderCommand.CloseOrder(orderId, reason, idempotencyKey));
        return ResponseEntity.ok(OrderResponse.from(queryService.getById(orderId)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam String idempotencyKey) {

        var orderId = new OrderId(id);
        applicationService.cancelOrder(new OrderCommand.CancelOrder(orderId, reason, idempotencyKey));
        return ResponseEntity.ok(OrderResponse.from(queryService.getById(orderId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        var order = queryService.getById(new OrderId(id));
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}