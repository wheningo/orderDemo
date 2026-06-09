package com.example.orderdemo.infrastructure.outbox;

import com.example.contracts.event.StockReserved;
import com.example.orderdemo.infrastructure.persistence.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.*;

class OutboxWriterTest {

    private OutboxMapper outboxMapper;
    private OutboxWriter writer;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        writer = new OutboxWriter(outboxMapper, objectMapper);
    }

    @Test
    void writesSerializedEventToOutbox() {
        var event = new StockReserved("evt-1", "SKU-1", 30, "tx-1", Instant.now());
        writer.write("Inventory", "SKU-1", "StockReserved", event);

        verify(outboxMapper).insert(argThat(outbox ->
            "Inventory".equals(outbox.getAggregateType()) &&
            "SKU-1".equals(outbox.getAggregateId()) &&
            "StockReserved".equals(outbox.getEventType()) &&
            outbox.getPayload().contains("SKU-1")
        ));
    }
}