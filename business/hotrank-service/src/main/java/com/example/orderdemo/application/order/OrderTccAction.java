package com.example.orderdemo.application.order;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

@LocalTCC
public interface OrderTccAction {

    @TwoPhaseBusinessAction(
            name = "orderTccAction",
            commitMethod = "confirm",
            rollbackMethod = "cancel",
            useTCCFence = false)
    boolean tryCreate(
            BusinessActionContext ctx,
            @BusinessActionContextParameter(paramName = "productName") String productName,
            @BusinessActionContextParameter(paramName = "quantity") int quantity);

    boolean confirm(BusinessActionContext ctx);

    boolean cancel(BusinessActionContext ctx);
}