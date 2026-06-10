package com.example.orderdemo.infrastructure.mq;

import com.example.orderdemo.domain.order.*;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@RocketMQMessageListener(topic = "delayed-commands", consumerGroup = "delayed-cmd-consumer")
public class DelayedCommandConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(DelayedCommandConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final InteractionEventDedup dedup;

    public DelayedCommandConsumer(ObjectMapper objectMapper, OrderRepository orderRepository, InteractionEventDedup dedup) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.dedup = dedup;
    }

    @Override
    public void onMessage(String message) {
        try {
            DelayedCommand cmd = objectMapper.readValue(message, DelayedCommand.class);
            if (dedup.isDuplicate(cmd.idempotencyKey())) {
                log.debug("Duplicate delayed command ignored: {}", cmd.idempotencyKey());
                return;
            }
            executeCommand(cmd);
        } catch (Exception e) {
            log.error("Failed to process delayed command: {}", message, e);
        }
    }

    private void executeCommand(DelayedCommand cmd) {
        switch (cmd.commandType()) {
            case "CLOSE_ORDER" -> closeOrder(cmd);
            default -> log.warn("Unknown delayed command type: {}", cmd.commandType());
        }
    }

    private void closeOrder(DelayedCommand cmd) {
        var orderId = new OrderId(Long.parseLong(cmd.targetId()));
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Delayed close: order not found, id={}", cmd.targetId());
            return;
        }
        if (!(order.state() instanceof OrderState.Created || order.state() instanceof OrderState.Pending)) {
            log.info("Delayed close: order not in closeable state, id={}, state={}", cmd.targetId(), order.state().description());
            return;
        }
        var closeCmd = new OrderCommand.CloseOrder(orderId, cmd.reason(), cmd.idempotencyKey());
        order.close(closeCmd);
        orderRepository.save(order);
        log.info("Delayed close executed: orderId={}, reason={}", cmd.targetId(), cmd.reason());
    }
}