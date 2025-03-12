package com.example.orderdemo.service;


import com.example.orderdemo.model.Created;
import com.example.orderdemo.model.Order;
import com.example.orderdemo.model.OrderState;
import com.example.orderdemo.repository.OrderRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Async
    @Transactional
    public CompletableFuture<Order> createOrder(String productName, int quantity) {
        logger.info("Attempting to create order for product: {}, quantity: {}", productName, quantity);
        RLock lock = redissonClient.getLock("order:lock:" + productName);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 尝试5秒获取锁，持有10秒
                Order order = new Order(null, productName, quantity, new Created(), null);
                Order savedOrder = orderRepository.save(order);
                kafkaTemplate.send("order-events", savedOrder.id().toString(), "ORDER_CREATED:" + savedOrder.id());
                return CompletableFuture.completedFuture(savedOrder);
            } else {
                logger.warn("Failed to acquire lock for product: {}", productName);
                throw new RuntimeException("Failed to acquire lock for " + productName);
            }
        } catch (InterruptedException e) {
            logger.error("Lock acquisition interrupted for product: {}", productName, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock interrupted", e);
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
        logger.info("Order state updated: id={}, state={}", id, savedOrder.state().getDescription());
        kafkaTemplate.send("order-events", id.toString(), "ORDER_UPDATED:" + id + ":" + newState.getDescription());
        return savedOrder;
    }



    @Cacheable(value = "orders", key = "#id")
    public Optional<Order> findById(Long id) {
        logger.debug("Fetching order from cache or DB: id={}", id);
        return orderRepository.findById(id);
    }

    @KafkaListener(topics = "order-events", groupId = "order-group")
    public void handleOrderEvents(String message) {
        logger.info("Received Kafka event: {}", message);
        if (message.startsWith("INVENTORY_FAILED:")) {
            Long orderId = Long.parseLong(message.split(":")[1]);
            rollbackOrder(orderId);
        }
    }

    @Transactional
    private void rollbackOrder(Long id) {
        logger.warn("Rolling back order: id={}", id);
        Order order = orderRepository.findById(id).orElseThrow();
        if (!(order.state() instanceof Created)) {
            Order rolledBack = new Order(order.id(), order.productName(), order.quantity(), new Created(), order.version());
            orderRepository.save(rolledBack);
            kafkaTemplate.send("order-events", id.toString(), "ORDER_ROLLED_BACK:" + id);
        }
    }
}
 
