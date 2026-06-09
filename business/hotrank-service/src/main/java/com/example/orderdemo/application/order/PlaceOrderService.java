package com.example.orderdemo.application.order;

import com.example.orderdemo.application.inventory.InventoryTccAction;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final OrderTccAction orderTccAction;
    private final InventoryTccAction inventoryTccAction;

    public PlaceOrderService(OrderTccAction orderTccAction, InventoryTccAction inventoryTccAction) {
        this.orderTccAction = orderTccAction;
        this.inventoryTccAction = inventoryTccAction;
    }

    @GlobalTransactional(name = "place-order", rollbackFor = Exception.class)
    public PlaceOrderResult placeOrder(String productName, int quantity, String sku) {
        String xid = RootContext.getXID();
        log.info("PlaceOrder started: product={}, qty={}, sku={}, xid={}", productName, quantity, sku, xid);

        try {
            boolean orderOk = orderTccAction.tryCreate(null, productName, quantity);
            if (!orderOk) {
                throw new RuntimeException("Order try failed");
            }

            boolean inventoryOk = inventoryTccAction.tryReserve(null, sku, quantity);
            if (!inventoryOk) {
                throw new OversellRejectedException("Inventory reservation failed for sku=" + sku + ", qty=" + quantity);
            }

            var orderId = OrderIdHolder.get();
            log.info("PlaceOrder try phase complete, global commit pending: product={}, qty={}, xid={}, orderId={}",
                    productName, quantity, xid, orderId != null ? orderId.value() : "null");
            return PlaceOrderResult.success(orderId, xid);
        } finally {
            OrderIdHolder.clear();
        }
    }
}