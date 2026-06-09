package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.OrderId;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderTccActionImpl implements OrderTccAction {

    private static final Logger log = LoggerFactory.getLogger(OrderTccActionImpl.class);

    private final OrderTccService orderTccService;

    public OrderTccActionImpl(OrderTccService orderTccService) {
        this.orderTccService = orderTccService;
    }

    @Override
    public boolean tryCreate(BusinessActionContext ctx, String productName, int quantity) {
        String txKey = ctx.getXid() + ":" + ctx.getBranchId();
        OrderId orderId = orderTccService.tryCreate(txKey, productName, quantity);
        ctx.addActionContext("orderId", orderId.value().toString());
        OrderIdHolder.set(orderId);
        return true;
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        String orderIdStr = (String) ctx.getActionContext("orderId");
        if (orderIdStr == null) {
            log.warn("TCC Confirm: orderId not in context, skip");
            return true;
        }
        orderTccService.confirm(new OrderId(Long.parseLong(orderIdStr)));
        return true;
    }

    @Override
    public boolean cancel(BusinessActionContext ctx) {
        String orderIdStr = (String) ctx.getActionContext("orderId");
        if (orderIdStr == null) {
            log.info("TCC Cancel: orderId not in context (empty rollback)");
            return true;
        }
        orderTccService.cancel(new OrderId(Long.parseLong(orderIdStr)));
        return true;
    }
}