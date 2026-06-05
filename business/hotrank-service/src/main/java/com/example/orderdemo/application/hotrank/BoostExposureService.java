package com.example.orderdemo.application.hotrank;

import com.example.orderdemo.domain.hotrank.BoostExposureCommand;
import com.example.orderdemo.domain.hotrank.BoostExposureResult;
import com.example.orderdemo.domain.hotrank.InteractionEvent;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.example.orderdemo.infrastructure.hotrank.HotRankMaterializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BoostExposureService {

    private static final Logger log = LoggerFactory.getLogger(BoostExposureService.class);

    private final InteractionEventDedup dedup;
    private final HotRankMaterializer materializer;

    public BoostExposureService(InteractionEventDedup dedup, HotRankMaterializer materializer) {
        this.dedup = dedup;
        this.materializer = materializer;
    }

    public BoostExposureResult execute(BoostExposureCommand cmd) {
        if (dedup.isDuplicate(cmd.idempotencyKey())) {
            log.info("Duplicate boost command ignored: key={}", cmd.idempotencyKey());
            return BoostExposureResult.duplicate(cmd.idempotencyKey());
        }

        InteractionEvent syntheticEvent = new InteractionEvent(
                UUID.randomUUID().toString(),
                cmd.targetContentId(),
                cmd.region(),
                "BOOST:" + cmd.decisionSource(),
                cmd.weight(),
                Instant.now()
        );

        materializer.handle(syntheticEvent);
        log.info("Boost applied: contentId={}, weight={}, region={}, source={}",
                cmd.targetContentId(), cmd.weight(), cmd.region(), cmd.decisionSource());
        return BoostExposureResult.accepted(cmd.idempotencyKey());
    }
}