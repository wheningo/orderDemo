package com.example.orderdemo.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @author whening
 * @description
 * @date 2025/3/13 00:29
 * @since 1.0
 **/
@Service
public class InventoryTCCServiceImpl implements InventoryTCCService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryTCCServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public boolean tryDeduct(BusinessActionContext actionContext, Long orderId, int quantity) {
        logger.info("TCC Try: Freezing inventory for orderId={}, quantity={}", orderId, quantity);
        int updated = jdbcTemplate.update(
                "UPDATE inventory SET frozen_stock = frozen_stock + ?, stock = stock - ? " +
                        "WHERE product_name = 'Coffee' AND stock >= ?",
                quantity, quantity, quantity
        );
        boolean success = updated > 0;
        if (!success) {
            logger.warn("TCC Try failed: Insufficient stock for orderId={}", orderId);
            throw new RuntimeException("Insufficient stock");
        }
        logger.info("TCC Try success: Frozen inventory for orderId={}", orderId);
        return true;
    }

    @Override
    @Transactional
    public boolean confirm(BusinessActionContext actionContext) {
        Long orderId = Long.valueOf((String) actionContext.getActionContext("orderId"));
        int quantity = (Integer) actionContext.getActionContext("quantity");
        logger.info("TCC Confirm: Deducting inventory for orderId={}, quantity={}", orderId, quantity);
        int updated = jdbcTemplate.update(
                "UPDATE inventory SET frozen_stock = frozen_stock - ? WHERE product_name = 'Coffee'",
                quantity
        );
        if (updated > 0) {
            logger.info("TCC Confirm success: Inventory deducted for orderId={}", orderId);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean cancel(BusinessActionContext actionContext) {
        Long orderId = Long.valueOf((String) actionContext.getActionContext("orderId"));
        int quantity = (Integer) actionContext.getActionContext("quantity");
        logger.info("TCC Cancel: Releasing inventory for orderId={}, quantity={}", orderId, quantity);
        int updated = jdbcTemplate.update(
                "UPDATE inventory SET frozen_stock = frozen_stock - ?, stock = stock + ? " +
                        "WHERE product_name = 'Coffee'",
                quantity, quantity
        );
        if (updated > 0) {
            logger.info("TCC Cancel success: Inventory released for orderId={}", orderId);
        }
        return true;
    }
}

 
