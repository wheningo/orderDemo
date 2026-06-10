package com.example.orderdemo.infrastructure.mq;

import com.example.orderdemo.domain.order.DelayedCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import java.time.Instant;

import static org.mockito.Mockito.*;

class DelayedCommandProducerTest {

    private RocketMQTemplate rocketMQTemplate;
    private DelayedCommandProducer producer;

    @BeforeEach
    void setUp() {
        rocketMQTemplate = mock(RocketMQTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        producer = new DelayedCommandProducer(rocketMQTemplate, objectMapper);
    }

    @Test
    void sendsDelayedMessage() {
        var cmd = new DelayedCommand("CLOSE_ORDER", "123", "timeout", "key-1", 5, Instant.now());
        producer.send(cmd);
        verify(rocketMQTemplate).syncSend(eq("delayed-commands"), any(Message.class), eq(3000L), eq(9));
    }

    @Test
    void mapsOneMinuteToLevel5() {
        var cmd = new DelayedCommand("CLOSE_ORDER", "123", "timeout", "key-2", 1, Instant.now());
        producer.send(cmd);
        verify(rocketMQTemplate).syncSend(eq("delayed-commands"), any(Message.class), eq(3000L), eq(5));
    }
}