package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaDomainEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaDomainEventPublisher(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void publishesOrderCreatedEvent() {
        var orderId = new OrderId(1L);
        var event = new OrderEvent.OrderCreated(orderId, "Coffee", 2, Instant.now());

        publisher.publish(event);

        var topicCaptor    = ArgumentCaptor.forClass(String.class);
        var keyCaptor      = ArgumentCaptor.forClass(String.class);
        var payloadCaptor  = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("order-events", topicCaptor.getValue());
        assertEquals("1", keyCaptor.getValue());
        assertTrue(payloadCaptor.getValue().startsWith("ORDER_CREATED:"));
        assertTrue(payloadCaptor.getValue().contains("Coffee"));
    }

    @Test
    void publishesOrderConfirmedEvent() {
        var event = new OrderEvent.OrderConfirmed(new OrderId(2L), Instant.now());
        publisher.publish(event);

        var payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("2"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().startsWith("ORDER_CONFIRMED:"));
    }

    @Test
    void publishesOrderClosedEvent() {
        var event = new OrderEvent.OrderClosed(new OrderId(3L), "timeout", Instant.now());
        publisher.publish(event);

        var payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("3"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().startsWith("ORDER_CLOSED:"));
        assertTrue(payloadCaptor.getValue().contains("timeout"));
    }

    @Test
    void publishesOrderCancelledEvent() {
        var event = new OrderEvent.OrderCancelled(new OrderId(4L), "user request", Instant.now());
        publisher.publish(event);

        var payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("4"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().startsWith("ORDER_CANCELLED:"));
        assertTrue(payloadCaptor.getValue().contains("user request"));
    }

    @Test
    void nullOrderIdUsesUnknownAsKey() {
        // OrderCreated for a brand-new order before id is assigned
        var event = new OrderEvent.OrderCreated(null, "Tea", 1, Instant.now());
        publisher.publish(event);

        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), keyCaptor.capture(), anyString());
        assertEquals("unknown", keyCaptor.getValue());
    }
}