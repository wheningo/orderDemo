package com.example.orderdemo.infrastructure.hotrank;

import com.example.orderdemo.domain.hotrank.InteractionEvent;
import com.example.orderdemo.infrastructure.kafka.InteractionEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class HotRankMaterializer implements InteractionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(HotRankMaterializer.class);
    private static final int BUCKET_MINUTES = 5;
    private static final int MAX_BUCKETS = 12;
    private static final Duration BUCKET_TTL = Duration.ofMinutes((long) BUCKET_MINUTES * MAX_BUCKETS);

    private final StringRedisTemplate redisTemplate;
    private final HotRankChangeDetector changeDetector;
    private final HotRankEventPublisher eventPublisher;

    public HotRankMaterializer(StringRedisTemplate redisTemplate,
                               HotRankChangeDetector changeDetector,
                               HotRankEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.changeDetector = changeDetector;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(InteractionEvent event) {
        String key = buildKey(event.region(), event.occurredAt());
        redisTemplate.opsForZSet().incrementScore(key, event.contentId(), event.weight());
        redisTemplate.expire(key, BUCKET_TTL);
        log.debug("Materialized: key={}, contentId={}, weight={}", key, event.contentId(), event.weight());

        changeDetector.detectChanges(event.region())
                .forEach(eventPublisher::publish);
    }

    String buildKey(String region, Instant timestamp) {
        long epochMinutes = timestamp.getEpochSecond() / 60;
        long bucket = epochMinutes / BUCKET_MINUTES;
        return "hotrank:" + region + ":" + bucket;
    }

    int getBucketMinutes() { return BUCKET_MINUTES; }
    int getMaxBuckets() { return MAX_BUCKETS; }
}