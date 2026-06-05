package com.example.orderdemo.infrastructure.hotrank;

import com.example.orderdemo.domain.hotrank.InteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotRankMaterializerTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOps;
    private HotRankChangeDetector changeDetector;
    private HotRankEventPublisher eventPublisher;
    private HotRankMaterializer materializer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        changeDetector = mock(HotRankChangeDetector.class);
        eventPublisher = mock(HotRankEventPublisher.class);
        when(changeDetector.detectChanges(anyString())).thenReturn(List.of());
        materializer = new HotRankMaterializer(redisTemplate, changeDetector, eventPublisher);
    }

    @Test
    void materializesEventToCorrectKey() {
        var event = new InteractionEvent("e1", "content-1", "CN", "LIKE", 1,
                Instant.parse("2026-06-01T10:03:00Z"));
        materializer.handle(event);

        String expectedKey = materializer.buildKey("CN", event.occurredAt());
        verify(zSetOps).incrementScore(expectedKey, "content-1", 1.0);
        verify(redisTemplate).expire(eq(expectedKey), any(Duration.class));
    }

    @Test
    void weightIsAccumulated() {
        var event = new InteractionEvent("e2", "content-1", "CN", "GIFT", 5,
                Instant.parse("2026-06-01T10:03:00Z"));
        materializer.handle(event);

        String expectedKey = materializer.buildKey("CN", event.occurredAt());
        verify(zSetOps).incrementScore(expectedKey, "content-1", 5.0);
    }

    @Test
    void differentRegionsGetDifferentKeys() {
        String keyCN = materializer.buildKey("CN", Instant.parse("2026-06-01T10:00:00Z"));
        String keyUS = materializer.buildKey("US", Instant.parse("2026-06-01T10:00:00Z"));
        assertNotEquals(keyCN, keyUS);
        assertTrue(keyCN.startsWith("hotrank:CN:"));
        assertTrue(keyUS.startsWith("hotrank:US:"));
    }

    @Test
    void differentBucketsForDifferentTimePeriods() {
        String key1 = materializer.buildKey("CN", Instant.parse("2026-06-01T10:00:00Z"));
        String key2 = materializer.buildKey("CN", Instant.parse("2026-06-01T10:06:00Z"));
        assertNotEquals(key1, key2);
    }

    @Test
    void sameBucketForCloseTimestamps() {
        String key1 = materializer.buildKey("CN", Instant.parse("2026-06-01T10:00:00Z"));
        String key2 = materializer.buildKey("CN", Instant.parse("2026-06-01T10:04:59Z"));
        assertEquals(key1, key2);
    }

    @Test
    void triggersChangeDetectionAfterMaterialization() {
        var event = new InteractionEvent("e3", "content-1", "CN", "LIKE", 1,
                Instant.parse("2026-06-01T10:03:00Z"));
        materializer.handle(event);
        verify(changeDetector).detectChanges("CN");
    }
}