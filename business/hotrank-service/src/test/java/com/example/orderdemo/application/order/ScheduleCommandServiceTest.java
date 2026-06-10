package com.example.orderdemo.application.order;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.example.orderdemo.infrastructure.mq.DelayedCommandProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleCommandServiceTest {

    private DelayedCommandProducer producer;
    private InteractionEventDedup dedup;
    private ScheduleCommandService service;

    @BeforeEach
    void setUp() {
        producer = mock(DelayedCommandProducer.class);
        dedup = mock(InteractionEventDedup.class);
        service = new ScheduleCommandService(producer, dedup);
    }

    @Test
    void schedulesSuccessfully() {
        when(dedup.isDuplicate("key-1")).thenReturn(false);
        CommandResult result = service.scheduleCloseOrder("123", "timeout", 5, "key-1");
        assertTrue(result.accepted());
        verify(producer).send(any());
    }

    @Test
    void duplicateIsIdempotent() {
        when(dedup.isDuplicate("key-dup")).thenReturn(true);
        CommandResult result = service.scheduleCloseOrder("123", "timeout", 5, "key-dup");
        assertTrue(result.accepted());
        verify(producer, never()).send(any());
    }
}