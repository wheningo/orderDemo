package com.example.orderdemo.infrastructure.outbox;

import com.example.orderdemo.infrastructure.mq.RocketMQ5Producer;
import com.example.orderdemo.infrastructure.persistence.OutboxDO;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private OutboxMapper outboxMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private RocketMQ5Producer rocketMQ5Producer;
    private OutboxRelay relay;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        rocketMQ5Producer = mock(RocketMQ5Producer.class);
        relay = new OutboxRelay(outboxMapper, kafkaTemplate, Optional.of(rocketMQ5Producer), new ObjectMapper());
    }

    @Test
    void relaysUnpublishedAndMarksPublished() {
        var row = new OutboxDO();
        row.setId(1L);
        row.setAggregateType("Inventory");
        row.setAggregateId("SKU-1");
        row.setEventType("StockReserved");
        row.setPayload("{\"sku\":\"SKU-1\"}");

        when(outboxMapper.findUnpublished(100)).thenReturn(List.of(row));
        when(kafkaTemplate.send("domain-events", "Inventory#SKU-1", "{\"sku\":\"SKU-1\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        verify(outboxMapper).markPublished(1L);
    }

    @Test
    void stopsOnFailure() {
        var row1 = new OutboxDO();
        row1.setId(1L);
        row1.setAggregateType("Inventory");
        row1.setAggregateId("SKU-1");
        row1.setEventType("StockReserved");
        row1.setPayload("{}");

        var row2 = new OutboxDO();
        row2.setId(2L);
        row2.setAggregateType("Inventory");
        row2.setAggregateId("SKU-2");
        row2.setEventType("StockReserved");
        row2.setPayload("{}");

        when(outboxMapper.findUnpublished(100)).thenReturn(List.of(row1, row2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        relay.relay();

        verify(outboxMapper, never()).markPublished(anyLong());
    }

    @Test
    void routesTimeoutGuardToRocketMQ() {
        var row = new OutboxDO();
        row.setId(3L);
        row.setAggregateType("Inventory");
        row.setAggregateId("SKU-1");
        row.setEventType("StockReservationTimeoutGuard");
        row.setPayload("{\"txKey\":\"tx-1\",\"sku\":\"SKU-1\",\"qty\":10,\"deliverTimeMillis\":1700000000000}");

        when(outboxMapper.findUnpublished(100)).thenReturn(List.of(row));

        relay.relay();

        verify(rocketMQ5Producer).sendScheduled("tx-1", row.getPayload(), 1700000000000L);
        verify(outboxMapper).markPublished(3L);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}