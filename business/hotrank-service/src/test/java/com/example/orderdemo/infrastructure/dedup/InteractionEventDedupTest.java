package com.example.orderdemo.infrastructure.dedup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InteractionEventDedupTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private InteractionEventDedup dedup;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        dedup = new InteractionEventDedup(redisTemplate);
    }

    @Test
    void firstEventIsNotDuplicate() {
        when(valueOps.setIfAbsent(eq("dedup:event:evt-001"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        assertFalse(dedup.isDuplicate("evt-001"));
    }

    @Test
    void secondEventWithSameIdIsDuplicate() {
        when(valueOps.setIfAbsent(eq("dedup:event:evt-001"), eq("1"), any(Duration.class)))
                .thenReturn(false);
        assertTrue(dedup.isDuplicate("evt-001"));
    }

    @Test
    void nullReturnTreatedAsDuplicate() {
        when(valueOps.setIfAbsent(eq("dedup:event:evt-002"), eq("1"), any(Duration.class)))
                .thenReturn(null);
        assertTrue(dedup.isDuplicate("evt-002"));
    }
}