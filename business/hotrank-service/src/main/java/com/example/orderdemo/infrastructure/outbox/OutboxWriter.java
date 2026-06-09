package com.example.orderdemo.infrastructure.outbox;

import com.example.orderdemo.infrastructure.persistence.OutboxDO;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    public void write(String aggregateType, String aggregateId, String eventType, Object event) {
        var outbox = new OutboxDO();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
        outboxMapper.insert(outbox);
    }
}