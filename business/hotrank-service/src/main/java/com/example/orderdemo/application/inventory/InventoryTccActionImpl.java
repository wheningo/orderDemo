package com.example.orderdemo.application.inventory;

import io.seata.rm.tcc.api.BusinessActionContext;
import com.example.contracts.result.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryTccActionImpl implements InventoryTccAction {

    private static final Logger log = LoggerFactory.getLogger(InventoryTccActionImpl.class);

    private final InventoryTccService inventoryTccService;

    public InventoryTccActionImpl(InventoryTccService inventoryTccService) {
        this.inventoryTccService = inventoryTccService;
    }

    @Override
    public boolean tryReserve(BusinessActionContext ctx, String sku, int qty) {
        String txKey = ctx.getXid() + ":" + ctx.getBranchId();
        CommandResult result = inventoryTccService.tryReserve(txKey, sku, qty);
        if (!result.accepted()) {
            log.warn("TCC Action tryReserve rejected: {}", result.reason());
            return false;
        }
        return true;
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        String txKey = ctx.getXid() + ":" + ctx.getBranchId();
        inventoryTccService.confirm(txKey);
        return true;
    }

    @Override
    public boolean cancel(BusinessActionContext ctx) {
        String txKey = ctx.getXid() + ":" + ctx.getBranchId();
        inventoryTccService.cancel(txKey);
        return true;
    }
}