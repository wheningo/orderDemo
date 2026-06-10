package com.example.orderdemo.infrastructure.outbox;

import com.example.orderdemo.infrastructure.mq.RocketMQ5Producer;
import com.example.orderdemo.infrastructure.persistence.OutboxDO;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final String TIMEOUT_GUARD_EVENT = "StockReservationTimeoutGuard";

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Optional<RocketMQ5Producer> rocketMQ5Producer;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxMapper outboxMapper, KafkaTemplate<String, String> kafkaTemplate,
                       Optional<RocketMQ5Producer> rocketMQ5Producer, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.rocketMQ5Producer = rocketMQ5Producer;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxDO> batch = outboxMapper.findUnpublished(BATCH_SIZE);
        for (OutboxDO row : batch) {
            try {
                if (TIMEOUT_GUARD_EVENT.equals(row.getEventType())) {
                    relayToRocketMQ(row);
                } else {
                    relayToKafka(row);
                }
                outboxMapper.markPublished(row.getId());
                log.debug("Relayed event: id={}, type={}", row.getId(), row.getEventType());
            } catch (Exception e) {
                log.warn("Failed to relay event id={}, will retry: {}", row.getId(), e.getMessage());
                break;
            }
        }
    }

    private void relayToKafka(OutboxDO row) throws Exception {
        String topic = "domain-events";
        String key = row.getAggregateType() + "#" + row.getAggregateId();
        kafkaTemplate.send(topic, key, row.getPayload()).get();
    }

    private void relayToRocketMQ(OutboxDO row) throws Exception {
        RocketMQ5Producer producer = rocketMQ5Producer.orElseThrow(
                () -> new IllegalStateException("RocketMQ5Producer not available, cannot relay timeout guard"));
        JsonNode payload = objectMapper.readTree(row.getPayload());
        String txKey = payload.get("txKey").asText();
        long deliverTimeMillis = payload.get("deliverTimeMillis").asLong();
        producer.sendScheduled(txKey, row.getPayload(), deliverTimeMillis);
    }
}