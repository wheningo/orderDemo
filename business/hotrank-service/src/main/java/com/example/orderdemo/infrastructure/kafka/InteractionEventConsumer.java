package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.domain.hotrank.InteractionEvent;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InteractionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InteractionEventConsumer.class);

    private final InteractionEventDedup dedup;
    private final ObjectMapper objectMapper;
    private final InteractionEventHandler handler;

    public InteractionEventConsumer(InteractionEventDedup dedup, ObjectMapper objectMapper, InteractionEventHandler handler) {
        this.dedup = dedup;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "interaction-events", groupId = "hotrank-consumer")
    public void consume(String message) {
        try {
            InteractionEvent event = objectMapper.readValue(message, InteractionEvent.class);
            if (dedup.isDuplicate(event.eventId())) {
                log.debug("Duplicate event ignored: {}", event.eventId());
                return;
            }
            handler.handle(event);
            log.debug("Event processed: {}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to process interaction event: {}", message, e);
        }
    }
}