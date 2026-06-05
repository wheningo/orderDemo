package com.example.orderdemo.domain.hotrank;

import java.time.Instant;
import java.util.UUID;

public record HotRankChangedEvent(
    String eventId,
    String contentId,
    String region,
    double previousScore,
    double currentScore,
    int previousRank,
    int currentRank,
    String changeType,  // ENTERED_TOP_K, EXITED_TOP_K, RANK_CHANGED
    Instant occurredAt
) {
    public static HotRankChangedEvent entered(String contentId, String region, double score, int rank) {
        return new HotRankChangedEvent(
                UUID.randomUUID().toString(), contentId, region, 0, score, 0, rank,
                "ENTERED_TOP_K", Instant.now());
    }

    public static HotRankChangedEvent exited(String contentId, String region, double previousScore, int previousRank) {
        return new HotRankChangedEvent(
                UUID.randomUUID().toString(), contentId, region, previousScore, 0, previousRank, 0,
                "EXITED_TOP_K", Instant.now());
    }

    public static HotRankChangedEvent rankChanged(String contentId, String region,
                                                   double prevScore, double curScore, int prevRank, int curRank) {
        return new HotRankChangedEvent(
                UUID.randomUUID().toString(), contentId, region, prevScore, curScore, prevRank, curRank,
                "RANK_CHANGED", Instant.now());
    }
}