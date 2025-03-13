package com.example.orderdemo.service;


import com.example.orderdemo.model.Order;
import com.example.orderdemo.model.OrderState;
import com.example.orderdemo.repository.OrderRepository;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author whening
 * @description
 * @date 2025/3/12 17:23
 * @since 1.0
 **/
@Service
public class OrderService {
    @Autowired private OrderRepository orderRepository;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private RedissonClient redissonClient;
    @Autowired private OrderTCCService orderTCCService;
    @Autowired private InventoryTCCService inventoryTCCService;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Async
    @GlobalTransactional(name = "create-order-tcc", rollbackFor = Exception.class)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Order> createOrder(String productName, int quantity) {
        logger.info("Starting TCC global transaction: product={}, quantity={}", productName, quantity);
        RLock lock = redissonClient.getLock("order:lock:" + productName);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 手动创建 BusinessActionContext
                BusinessActionContext actionContext = new BusinessActionContext();
                Map<String, Object> actionContextMap = new HashMap<>();
                actionContext.setActionContext(actionContextMap);

                // 调用 TCC Try 方法
                Long orderId = orderTCCService.tryCreate(actionContext, productName, quantity);

                // 将 orderId 和 quantity 存入上下文供后续使用
                actionContext.getActionContext().put("orderId", orderId.toString());
                actionContext.getActionContext().put("quantity", quantity);

                // 调用库存 TCC Try 方法
                boolean inventorySuccess = inventoryTCCService.tryDeduct(actionContext, orderId, quantity);
                if (!inventorySuccess) {
                    logger.warn("TCC Try failed: Inventory deduction failed for orderId={}", orderId);
                    throw new RuntimeException("Inventory deduction failed");
                }

                kafkaTemplate.send("order-events", orderId.toString(), "ORDER_CREATED:" + orderId);
                logger.info("TCC Global transaction completed: orderId={}", orderId);

                Order order = orderRepository.findById(orderId).orElseThrow();
                return CompletableFuture.completedFuture(order);
            } else {
                logger.warn("Lock timeout for product: {}", productName);
                throw new RuntimeException("Lock timeout");
            }
        } catch (InterruptedException e) {
            logger.error("Lock interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock interrupted", e);
        } catch (Exception e) {
            logger.error("Order creation failed: product={}, quantity={}", productName, quantity, e);
            throw e;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.debug("Lock released for product: {}", productName);
            }
        }
    }


    @Transactional
    public Order updateState(Long id, OrderState newState) {
        logger.info("Updating order state: id={}, newState={}", id, newState.getDescription());
        Order order = orderRepository.findById(id).orElseThrow();
        Order updatedOrder = new Order(order.id(), order.productName(), order.quantity(), newState, order.version());
        Order savedOrder = orderRepository.save(updatedOrder);
        logger.info("Order state updated: id={}", id);
        kafkaTemplate.send("order-events", id.toString(), "ORDER_UPDATED:" + id + ":" + newState.getDescription());
        return savedOrder;
    }

    @Cacheable(value = "orders", key = "#id")
    public Optional<Order> findById(Long id) {
        logger.debug("Fetching order: id={}", id);
        return orderRepository.findById(id);
    }
}
 
