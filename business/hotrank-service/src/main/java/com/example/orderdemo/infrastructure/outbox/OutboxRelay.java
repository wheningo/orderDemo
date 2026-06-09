package com.example.orderdemo.infrastructure.outbox;

import com.example.orderdemo.infrastructure.persistence.OutboxDO;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxMapper outboxMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxDO> batch = outboxMapper.findUnpublished(BATCH_SIZE);
        for (OutboxDO row : batch) {
            String topic = "domain-events";
            String key = row.getAggregateType() + "#" + row.getAggregateId();
            try {
                kafkaTemplate.send(topic, key, row.getPayload()).get();
                outboxMapper.markPublished(row.getId());
                log.debug("Relayed event: id={}, type={}", row.getId(), row.getEventType());
            } catch (Exception e) {
                log.warn("Failed to relay event id={}, will retry: {}", row.getId(), e.getMessage());
                break;
            }
        }
    }
}