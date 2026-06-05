package com.example.orderdemo.infrastructure.hotrank;

import com.example.orderdemo.application.hotrank.HotRankQueryService;
import com.example.orderdemo.application.hotrank.RankedContent;
import com.example.orderdemo.domain.hotrank.HotRankChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Component
public class HotRankChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(HotRankChangeDetector.class);
    private static final int TOP_K = 10;

    private final HotRankQueryService queryService;
    private final Map<String, List<RankedContent>> previousSnapshots = new ConcurrentHashMap<>();

    public HotRankChangeDetector(HotRankQueryService queryService) {
        this.queryService = queryService;
    }

    public List<HotRankChangedEvent> detectChanges(String region) {
        List<RankedContent> current = queryService.getTopK(region, TOP_K);
        List<RankedContent> previous = previousSnapshots.getOrDefault(region, List.of());
        previousSnapshots.put(region, current);

        List<HotRankChangedEvent> events = new ArrayList<>();

        Map<String, Integer> prevRankMap = new HashMap<>();
        Map<String, Double> prevScoreMap = new HashMap<>();
        IntStream.range(0, previous.size()).forEach(i -> {
            prevRankMap.put(previous.get(i).contentId(), i + 1);
            prevScoreMap.put(previous.get(i).contentId(), previous.get(i).score());
        });

        for (int i = 0; i < current.size(); i++) {
            RankedContent item = current.get(i);
            int curRank = i + 1;
            if (!prevRankMap.containsKey(item.contentId())) {
                events.add(HotRankChangedEvent.entered(item.contentId(), region, item.score(), curRank));
            } else {
                int prevRank = prevRankMap.get(item.contentId());
                if (prevRank != curRank) {
                    events.add(HotRankChangedEvent.rankChanged(item.contentId(), region,
                            prevScoreMap.getOrDefault(item.contentId(), 0.0), item.score(), prevRank, curRank));
                }
            }
        }

        Map<String, Integer> curRankMap = new HashMap<>();
        IntStream.range(0, current.size()).forEach(i -> curRankMap.put(current.get(i).contentId(), i + 1));
        for (int i = 0; i < previous.size(); i++) {
            RankedContent item = previous.get(i);
            if (!curRankMap.containsKey(item.contentId())) {
                events.add(HotRankChangedEvent.exited(item.contentId(), region, item.score(), i + 1));
            }
        }

        if (!events.isEmpty()) {
            log.info("Detected {} rank changes in region {}", events.size(), region);
        }
        return events;
    }
}