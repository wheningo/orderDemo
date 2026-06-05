package com.example.orderdemo.infrastructure.dedup;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class InteractionEventDedup {

    private static final String KEY_PREFIX = "dedup:event:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public InteractionEventDedup(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(String eventId) {
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
        return !Boolean.TRUE.equals(wasSet);
    }
}