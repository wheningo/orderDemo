package com.example.orderdemo.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * @author whening
 * @description
 * @date 2025/3/13 00:24
 * @since 1.0
 **/
@LocalTCC
public interface OrderTCCService {

    @TwoPhaseBusinessAction(name = "createOrderTCC", commitMethod = "confirm", rollbackMethod = "cancel")
    Long tryCreate(
            BusinessActionContext actionContext,
            @BusinessActionContextParameter(paramName = "productName") String productName,
            @BusinessActionContextParameter(paramName = "quantity") int quantity
    );

    boolean confirm(BusinessActionContext actionContext);

    boolean cancel(BusinessActionContext actionContext);
}
