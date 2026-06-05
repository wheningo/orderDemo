package com.example.orderdemo.application.hotrank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotRankQueryServiceTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOps;
    private HotRankQueryService queryService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        queryService = new HotRankQueryService(redisTemplate);
    }

    @Test
    void recentBucketKeysGenerates12Keys() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        List<String> keys = queryService.recentBucketKeys("CN", now);
        assertEquals(12, keys.size());
        assertTrue(keys.getFirst().startsWith("hotrank:CN:"));
        assertTrue(keys.getLast().startsWith("hotrank:CN:"));
    }

    @Test
    void topKReturnsResultsInDescendingOrder() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        List<String> keys = queryService.recentBucketKeys("CN", now);
        String tempKey = "hotrank:tmp:CN:" + Thread.currentThread().threadId();

        when(zSetOps.unionAndStore(eq(keys.getFirst()), eq(keys.subList(1, keys.size())), eq(tempKey)))
                .thenReturn(3L);
        when(zSetOps.reverseRangeWithScores(tempKey, 0, 2))
                .thenReturn(Set.of(
                        ZSetOperations.TypedTuple.of("c-1", 100.0),
                        ZSetOperations.TypedTuple.of("c-2", 80.0),
                        ZSetOperations.TypedTuple.of("c-3", 50.0)
                ));

        List<RankedContent> result = queryService.getTopK("CN", 3, now);
        assertEquals(3, result.size());
    }

    @Test
    void topKReturnsEmptyWhenNoData() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        String tempKey = "hotrank:tmp:CN:" + Thread.currentThread().threadId();

        when(zSetOps.unionAndStore(anyString(), anyList(), eq(tempKey))).thenReturn(0L);
        when(zSetOps.reverseRangeWithScores(eq(tempKey), eq(0L), anyLong())).thenReturn(null);

        List<RankedContent> result = queryService.getTopK("CN", 10, now);
        assertTrue(result.isEmpty());
    }

    @Test
    void differentRegionsQueryDifferentKeys() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        List<String> keysCN = queryService.recentBucketKeys("CN", now);
        List<String> keysUS = queryService.recentBucketKeys("US", now);
        assertNotEquals(keysCN, keysUS);
        assertTrue(keysCN.stream().allMatch(k -> k.contains(":CN:")));
        assertTrue(keysUS.stream().allMatch(k -> k.contains(":US:")));
    }

    @Test
    void tempKeyIsCleanedUp() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        String tempKey = "hotrank:tmp:CN:" + Thread.currentThread().threadId();

        when(zSetOps.unionAndStore(anyString(), anyList(), eq(tempKey))).thenReturn(0L);
        when(zSetOps.reverseRangeWithScores(eq(tempKey), eq(0L), anyLong())).thenReturn(null);

        queryService.getTopK("CN", 10, now);
        verify(redisTemplate).delete(tempKey);
    }
}