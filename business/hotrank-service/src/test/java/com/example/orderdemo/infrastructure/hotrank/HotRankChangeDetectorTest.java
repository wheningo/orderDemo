package com.example.orderdemo.infrastructure.hotrank;

import com.example.orderdemo.application.hotrank.HotRankQueryService;
import com.example.orderdemo.application.hotrank.RankedContent;
import com.example.orderdemo.domain.hotrank.HotRankChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotRankChangeDetectorTest {

    private HotRankQueryService queryService;
    private HotRankChangeDetector detector;

    @BeforeEach
    void setUp() {
        queryService = mock(HotRankQueryService.class);
        detector = new HotRankChangeDetector(queryService);
    }

    @Test
    void firstCallDetectsAllAsEntered() {
        when(queryService.getTopK("CN", 10)).thenReturn(List.of(
                new RankedContent("c-1", 100),
                new RankedContent("c-2", 80)
        ));
        List<HotRankChangedEvent> events = detector.detectChanges("CN");
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> "ENTERED_TOP_K".equals(e.changeType())));
    }

    @Test
    void noChangeProducesNoEvents() {
        var snapshot = List.of(new RankedContent("c-1", 100), new RankedContent("c-2", 80));
        when(queryService.getTopK("CN", 10)).thenReturn(snapshot);
        detector.detectChanges("CN"); // first call sets baseline

        when(queryService.getTopK("CN", 10)).thenReturn(snapshot);
        List<HotRankChangedEvent> events = detector.detectChanges("CN");
        assertTrue(events.isEmpty());
    }

    @Test
    void rankSwapDetected() {
        when(queryService.getTopK("CN", 10)).thenReturn(List.of(
                new RankedContent("c-1", 100), new RankedContent("c-2", 80)));
        detector.detectChanges("CN");

        when(queryService.getTopK("CN", 10)).thenReturn(List.of(
                new RankedContent("c-2", 110), new RankedContent("c-1", 100)));
        List<HotRankChangedEvent> events = detector.detectChanges("CN");
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> "RANK_CHANGED".equals(e.changeType())));
    }

    @Test
    void exitDetectedWhenItemLeavesTopK() {
        when(queryService.getTopK("CN", 10)).thenReturn(List.of(
                new RankedContent("c-1", 100), new RankedContent("c-2", 80)));
        detector.detectChanges("CN");

        when(queryService.getTopK("CN", 10)).thenReturn(List.of(
                new RankedContent("c-1", 100), new RankedContent("c-3", 90)));
        List<HotRankChangedEvent> events = detector.detectChanges("CN");
        assertTrue(events.stream().anyMatch(e -> "EXITED_TOP_K".equals(e.changeType()) && "c-2".equals(e.contentId())));
        assertTrue(events.stream().anyMatch(e -> "ENTERED_TOP_K".equals(e.changeType()) && "c-3".equals(e.contentId())));
    }
}