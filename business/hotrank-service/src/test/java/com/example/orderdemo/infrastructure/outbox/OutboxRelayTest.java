package com.example.orderdemo.infrastructure.outbox;

import com.example.orderdemo.infrastructure.persistence.OutboxDO;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private OutboxMapper outboxMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelay relay;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        relay = new OutboxRelay(outboxMapper, kafkaTemplate);
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
        row1.setPayload("{}");

        var row2 = new OutboxDO();
        row2.setId(2L);
        row2.setAggregateType("Inventory");
        row2.setAggregateId("SKU-2");
        row2.setPayload("{}");

        when(outboxMapper.findUnpublished(100)).thenReturn(List.of(row1, row2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        relay.relay();

        verify(outboxMapper, never()).markPublished(anyLong());
    }
}