package com.example.orderdemo.service;

import org.springframework.stereotype.Service;
import com.example.orderdemo.model.Order;
import com.example.orderdemo.model.Cancelled;
import com.example.orderdemo.model.Confirmed;
import com.example.orderdemo.model.Created;
import com.example.orderdemo.repository.OrderRepository;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
/**
 * @author whening
 * @description
 * @date 2025/3/13 00:25
 * @since 1.0
 **/
@Service
public class OrderTCCServiceImpl implements OrderTCCService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTCCServiceImpl.class);

    @Autowired private OrderRepository orderRepository;

    @Override
    @Transactional
    public Long tryCreate(BusinessActionContext actionContext, String productName, int quantity) {
        logger.info("TCC Try: Creating temporary order for product={}, quantity={}", productName, quantity);
        Order order = new Order(null, productName, quantity, new Created(), null);
        Order savedOrder = orderRepository.save(order);
        actionContext.getActionContext().put("orderId", savedOrder.id().toString());
        logger.info("TCC Try success: Temporary order created, id={}", savedOrder.id());
        return savedOrder.id(); // 返回订单 ID
    }


    @Override
    @Transactional
    public boolean confirm(BusinessActionContext actionContext) {
        Long orderId = Long.valueOf((String) actionContext.getActionContext("orderId"));
        logger.info("TCC Confirm: Confirming order, id={}", orderId);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.state() instanceof Confirmed) {
            logger.info("TCC Confirm: Order already confirmed or not found, id={}", orderId);
            return true;
        }

        Order confirmedOrder = new Order(order.id(), order.productName(), order.quantity(), new Confirmed(), order.version());
        orderRepository.save(confirmedOrder);
        logger.info("TCC Confirm success: Order confirmed, id={}", orderId);
        return true;
    }

    @Override
    @Transactional
    public boolean cancel(BusinessActionContext actionContext) {
        Long orderId = Long.valueOf((String) actionContext.getActionContext("orderId"));
        logger.info("TCC Cancel: Cancelling order, id={}", orderId);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.state() instanceof Cancelled) {
            logger.info("TCC Cancel: Order already cancelled or not found, id={}", orderId);
            return true;
        }

        Order cancelledOrder = new Order(order.id(), order.productName(), order.quantity(), new Cancelled(), order.version());
        orderRepository.save(cancelledOrder);
        logger.info("TCC Cancel success: Order cancelled, id={}", orderId);
        return true;
    }

}
 
