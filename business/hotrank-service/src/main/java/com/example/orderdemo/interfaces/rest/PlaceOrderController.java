package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.PlaceOrderResult;
import com.example.orderdemo.application.order.PlaceOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class PlaceOrderController {

    private final PlaceOrderService placeOrderService;

    public PlaceOrderController(PlaceOrderService placeOrderService) {
        this.placeOrderService = placeOrderService;
    }

    @PostMapping("/place")
    public ResponseEntity<PlaceOrderResult> placeOrder(@RequestBody Map<String, Object> body) {
        String productName = (String) body.get("productName");
        int quantity = ((Number) body.get("quantity")).intValue();
        String sku = (String) body.get("sku");

        PlaceOrderResult result = placeOrderService.placeOrder(productName, quantity, sku);
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.unprocessableEntity().body(result);
    }
}