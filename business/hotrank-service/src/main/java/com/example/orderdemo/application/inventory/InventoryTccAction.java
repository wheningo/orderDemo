package com.example.orderdemo.application.inventory;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface InventoryTccAction {

    @TwoPhaseBusinessAction(
            name = "inventoryTccAction",
            commitMethod = "confirm",
            rollbackMethod = "cancel",
            useTCCFence = false)
    boolean tryReserve(
            BusinessActionContext ctx,
            @BusinessActionContextParameter(paramName = "sku") String sku,
            @BusinessActionContextParameter(paramName = "qty") int qty);

    boolean confirm(BusinessActionContext ctx);

    boolean cancel(BusinessActionContext ctx);
}