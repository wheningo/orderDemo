package com.example.orderdemo.application.order;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.application.inventory.InventoryTccService;
import com.example.orderdemo.domain.order.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final OrderTccService orderTccService;
    private final InventoryTccService inventoryTccService;

    public PlaceOrderService(OrderTccService orderTccService, InventoryTccService inventoryTccService) {
        this.orderTccService = orderTccService;
        this.inventoryTccService = inventoryTccService;
    }

    // When Seata TC is available, add @GlobalTransactional here.
    // Without TC, this manual coordination works because each branch
    // runs in its own REQUIRES_NEW transaction.
    public PlaceOrderResult placeOrder(String productName, int quantity, String sku) {
        String txKey = UUID.randomUUID().toString();
        log.info("PlaceOrder started: product={}, qty={}, sku={}, txKey={}", productName, quantity, sku, txKey);

        // Phase 1: Try (each in its own transaction)
        OrderId orderId = null;
        CommandResult inventoryResult;
        try {
            orderId = orderTccService.tryCreate(txKey, productName, quantity);
            inventoryResult = inventoryTccService.tryReserve(txKey, sku, quantity);
        } catch (Exception e) {
            log.error("Try phase failed: {}", e.getMessage());
            cancelAll(txKey, orderId);
            return PlaceOrderResult.failed("Try phase failed: " + e.getMessage());
        }

        if (!inventoryResult.accepted()) {
            log.warn("Inventory try rejected: {}", inventoryResult.reason());
            cancelAll(txKey, orderId);
            return PlaceOrderResult.failed(inventoryResult.reason());
        }

        // Phase 2: Confirm (each in its own transaction)
        try {
            orderTccService.confirm(orderId);
            inventoryTccService.confirm(txKey);
            log.info("PlaceOrder confirmed: orderId={}, txKey={}", orderId.value(), txKey);
            return PlaceOrderResult.success(orderId, txKey);
        } catch (Exception e) {
            log.error("Confirm phase failed, attempting cancel: {}", e.getMessage());
            cancelAll(txKey, orderId);
            return PlaceOrderResult.failed("Confirm failed: " + e.getMessage());
        }
    }

    private void cancelAll(String txKey, OrderId orderId) {
        try {
            inventoryTccService.cancel(txKey);
        } catch (Exception e) {
            log.error("Inventory cancel failed: txKey={}", txKey, e);
        }
        if (orderId != null) {
            try {
                orderTccService.cancel(orderId);
            } catch (Exception e) {
                log.error("Order cancel failed: orderId={}", orderId.value(), e);
            }
        }
    }
}