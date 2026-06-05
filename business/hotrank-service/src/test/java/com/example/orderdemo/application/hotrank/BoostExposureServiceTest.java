package com.example.orderdemo.application.hotrank;

import com.example.orderdemo.domain.hotrank.BoostExposureCommand;
import com.example.orderdemo.domain.hotrank.BoostExposureResult;
import com.example.orderdemo.domain.hotrank.InteractionEvent;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.example.orderdemo.infrastructure.hotrank.HotRankMaterializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoostExposureServiceTest {

    private InteractionEventDedup dedup;
    private HotRankMaterializer materializer;
    private BoostExposureService service;

    @BeforeEach
    void setUp() {
        dedup = mock(InteractionEventDedup.class);
        materializer = mock(HotRankMaterializer.class);
        service = new BoostExposureService(dedup, materializer);
    }

    @Test
    void acceptsValidBoostCommand() {
        when(dedup.isDuplicate("key-1")).thenReturn(false);
        var cmd = new BoostExposureCommand("content-1", 10, "CN", "key-1", "agent");
        BoostExposureResult result = service.execute(cmd);

        assertTrue(result.accepted());
        assertNull(result.reason());
        verify(materializer).handle(any(InteractionEvent.class));
    }

    @Test
    void rejectsDuplicateCommand() {
        when(dedup.isDuplicate("key-dup")).thenReturn(true);
        var cmd = new BoostExposureCommand("content-1", 10, "CN", "key-dup", "agent");
        BoostExposureResult result = service.execute(cmd);

        assertTrue(result.accepted());
        assertEquals("duplicate", result.reason());
        verify(materializer, never()).handle(any());
    }

    @Test
    void rejectsWeightTooLow() {
        assertThrows(IllegalArgumentException.class,
            () -> new BoostExposureCommand("c-1", 0, "CN", "key", "agent"));
    }

    @Test
    void rejectsWeightTooHigh() {
        assertThrows(IllegalArgumentException.class,
            () -> new BoostExposureCommand("c-1", 101, "CN", "key", "agent"));
    }

    @Test
    void passesCorrectDataToMaterializer() {
        when(dedup.isDuplicate("key-2")).thenReturn(false);
        var cmd = new BoostExposureCommand("target-x", 25, "US", "key-2", "manual");
        service.execute(cmd);

        verify(materializer).handle(argThat(event ->
            "target-x".equals(event.contentId()) &&
            "US".equals(event.region()) &&
            event.weight() == 25 &&
            "BOOST:manual".equals(event.interactionType())
        ));
    }
}