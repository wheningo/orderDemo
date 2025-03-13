package com.example.orderdemo.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
/**
 * @author whening
 * @description
 * @date 2025/3/13 00:28
 * @since 1.0
 **/
@LocalTCC
public interface InventoryTCCService {

    @TwoPhaseBusinessAction(name = "deductInventoryTCC", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean tryDeduct(
            BusinessActionContext actionContext,
            @BusinessActionContextParameter(paramName = "orderId") Long orderId,
            @BusinessActionContextParameter(paramName = "quantity") int quantity
    );

    boolean confirm(BusinessActionContext actionContext);

    boolean cancel(BusinessActionContext actionContext);
}
