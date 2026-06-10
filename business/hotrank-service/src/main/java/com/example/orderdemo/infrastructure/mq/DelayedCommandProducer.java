package com.example.orderdemo.infrastructure.mq;

import com.example.orderdemo.domain.order.DelayedCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class DelayedCommandProducer {

    private static final Logger log = LoggerFactory.getLogger(DelayedCommandProducer.class);
    private static final String TOPIC = "delayed-commands";

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public DelayedCommandProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(DelayedCommand cmd) {
        try {
            String payload = objectMapper.writeValueAsString(cmd);
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("KEYS", cmd.idempotencyKey())
                    .build();
            // RocketMQ delay levels: 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m...
            int delayLevel = mapMinutesToDelayLevel(cmd.delayMinutes());
            rocketMQTemplate.syncSend(TOPIC, message, 3000, delayLevel);
            log.info("Delayed command scheduled: type={}, targetId={}, delayMin={}, key={}",
                    cmd.commandType(), cmd.targetId(), cmd.delayMinutes(), cmd.idempotencyKey());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize delayed command", e);
        }
    }

    private int mapMinutesToDelayLevel(int minutes) {
        // RocketMQ 5.x delay levels: 1s,5s,10s,30s,1m,2m,3m,4m,5m,6m,7m,8m,9m,10m,20m,30m,1h,2h
        if (minutes <= 1) return 5;
        if (minutes <= 2) return 6;
        if (minutes <= 3) return 7;
        if (minutes <= 5) return 9;
        if (minutes <= 10) return 14;
        if (minutes <= 20) return 15;
        if (minutes <= 30) return 16;
        return 17; // 1h
    }
}