package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.domain.hotrank.InteractionEvent;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class InteractionEventConsumerTest {

    private InteractionEventDedup dedup;
    private InteractionEventHandler handler;
    private InteractionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        dedup = mock(InteractionEventDedup.class);
        handler = mock(InteractionEventHandler.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        consumer = new InteractionEventConsumer(dedup, mapper, handler);
    }

    @Test
    void processesNewEvent() {
        when(dedup.isDuplicate("evt-1")).thenReturn(false);
        String json = """
            {"eventId":"evt-1","contentId":"c-100","region":"CN","interactionType":"LIKE","weight":1,"occurredAt":"2026-06-01T00:00:00Z"}
            """;
        consumer.consume(json);
        verify(handler).handle(any(InteractionEvent.class));
    }

    @Test
    void skipsDuplicateEvent() {
        when(dedup.isDuplicate("evt-1")).thenReturn(true);
        String json = """
            {"eventId":"evt-1","contentId":"c-100","region":"CN","interactionType":"LIKE","weight":1,"occurredAt":"2026-06-01T00:00:00Z"}
            """;
        consumer.consume(json);
        verify(handler, never()).handle(any());
    }

    @Test
    void handlesInvalidJsonGracefully() {
        consumer.consume("not json");
        verify(handler, never()).handle(any());
    }
}