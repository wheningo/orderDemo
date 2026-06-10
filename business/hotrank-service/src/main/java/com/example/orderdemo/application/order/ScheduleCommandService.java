package com.example.orderdemo.application.order;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.order.DelayedCommand;
import com.example.orderdemo.infrastructure.dedup.InteractionEventDedup;
import com.example.orderdemo.infrastructure.mq.DelayedCommandProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnBean(DelayedCommandProducer.class)
public class ScheduleCommandService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCommandService.class);

    private final DelayedCommandProducer producer;
    private final InteractionEventDedup dedup;

    public ScheduleCommandService(DelayedCommandProducer producer, InteractionEventDedup dedup) {
        this.producer = producer;
        this.dedup = dedup;
    }

    public CommandResult scheduleCloseOrder(String orderId, String reason, int delayMinutes, String idempotencyKey) {
        if (dedup.isDuplicate(idempotencyKey)) {
            log.info("Duplicate schedule ignored: key={}", idempotencyKey);
            return CommandResult.ok();
        }

        var cmd = new DelayedCommand("CLOSE_ORDER", orderId, reason, idempotencyKey, delayMinutes, Instant.now());
        producer.send(cmd);
        log.info("Scheduled close order: orderId={}, delayMin={}, key={}", orderId, delayMinutes, idempotencyKey);
        return CommandResult.ok();
    }
}