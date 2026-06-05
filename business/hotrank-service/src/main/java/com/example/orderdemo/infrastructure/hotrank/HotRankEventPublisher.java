package com.example.orderdemo.infrastructure.hotrank;

import com.example.orderdemo.domain.hotrank.HotRankChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class HotRankEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(HotRankEventPublisher.class);
    private static final String TOPIC = "hotrank-changes";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public HotRankEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(HotRankChangedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.contentId(), payload);
            log.debug("Published HotRankChanged: eventId={}, type={}", event.eventId(), event.changeType());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize HotRankChangedEvent: {}", event.eventId(), e);
        }
    }
}