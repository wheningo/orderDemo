package com.example.orderdemo.application.hotrank;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class HotRankQueryService {

    private static final int BUCKET_MINUTES = 5;
    private static final int MAX_BUCKETS = 12;

    private final StringRedisTemplate redisTemplate;

    public HotRankQueryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<RankedContent> getTopK(String region, int k) {
        return getTopK(region, k, Instant.now());
    }

    List<RankedContent> getTopK(String region, int k, Instant now) {
        List<String> keys = recentBucketKeys(region, now);
        if (keys.isEmpty()) return Collections.emptyList();

        String tempKey = "hotrank:tmp:" + region + ":" + Thread.currentThread().threadId();
        try {
            redisTemplate.opsForZSet().unionAndStore(keys.getFirst(), keys.subList(1, keys.size()), tempKey);
            Set<ZSetOperations.TypedTuple<String>> results =
                    redisTemplate.opsForZSet().reverseRangeWithScores(tempKey, 0, k - 1);
            if (results == null) return Collections.emptyList();
            return results.stream()
                    .map(t -> new RankedContent(t.getValue(), t.getScore() != null ? t.getScore() : 0.0))
                    .toList();
        } finally {
            redisTemplate.delete(tempKey);
        }
    }

    List<String> recentBucketKeys(String region, Instant now) {
        long currentBucket = (now.getEpochSecond() / 60) / BUCKET_MINUTES;
        return java.util.stream.LongStream.rangeClosed(currentBucket - MAX_BUCKETS + 1, currentBucket)
                .mapToObj(b -> "hotrank:" + region + ":" + b)
                .toList();
    }
}