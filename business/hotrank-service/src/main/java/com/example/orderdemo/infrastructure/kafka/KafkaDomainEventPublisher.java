package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.application.order.OrderEventPublisher;
import com.example.orderdemo.domain.order.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events to the 'order-events' Kafka topic.
 * Uses pattern-matching switch to serialize each event type to a string payload.
 * OrderId is used as the partition key to preserve per-order ordering.
 */
@Component
public class KafkaDomainEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);
    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OrderEvent event) {
        String key     = resolveKey(event);
        String payload = serialize(event);

        kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} for key {}", payload, key, ex);
                    } else {
                        log.debug("Published event to {}/{}: {}", TOPIC, key, payload);
                    }
                });
    }

    // --- helpers ---

    private static String resolveKey(OrderEvent event) {
        var orderId = switch (event) {
            case OrderEvent.OrderCreated   e -> e.orderId();
            case OrderEvent.OrderConfirmed e -> e.orderId();
            case OrderEvent.OrderClosed    e -> e.orderId();
            case OrderEvent.OrderCancelled e -> e.orderId();
        };
        // orderId may be null for a freshly-created order before id assignment
        return orderId != null ? orderId.value().toString() : "unknown";
    }

    private static String serialize(OrderEvent event) {
        return switch (event) {
            case OrderEvent.OrderCreated e ->
                    "ORDER_CREATED:orderId=%s,product=%s,quantity=%d,at=%s"
                            .formatted(keyStr(e.orderId()), e.productName(), e.quantity(), e.occurredAt());
            case OrderEvent.OrderConfirmed e ->
                    "ORDER_CONFIRMED:orderId=%s,at=%s"
                            .formatted(keyStr(e.orderId()), e.occurredAt());
            case OrderEvent.OrderClosed e ->
                    "ORDER_CLOSED:orderId=%s,reason=%s,at=%s"
                            .formatted(keyStr(e.orderId()), e.reason(), e.occurredAt());
            case OrderEvent.OrderCancelled e ->
                    "ORDER_CANCELLED:orderId=%s,reason=%s,at=%s"
                            .formatted(keyStr(e.orderId()), e.reason(), e.occurredAt());
        };
    }

    private static String keyStr(com.example.orderdemo.domain.order.OrderId id) {
        return id != null ? id.value().toString() : "null";
    }
}