package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import com.example.orderdemo.infrastructure.outbox.OutboxWriter;
import com.example.orderdemo.infrastructure.persistence.ReservationDO;
import com.example.orderdemo.infrastructure.persistence.ReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryTccServiceTest {

    private InventoryRepository repository;
    private ReservationMapper reservationMapper;
    private OutboxWriter outboxWriter;
    private InventoryTccService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        reservationMapper = mock(ReservationMapper.class);
        outboxWriter = mock(OutboxWriter.class);
        service = new InventoryTccService(repository, reservationMapper, outboxWriter);
    }

    @Test
    void tryReserveSucceeds() {
        when(reservationMapper.findByTxKey("tx-1")).thenReturn(Optional.empty());
        var inv = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));

        CommandResult result = service.tryReserve("tx-1", "SKU-1", 30);
        assertTrue(result.accepted());
        verify(reservationMapper).insert("tx-1", "SKU-1", 30, "TRIED");
    }

    @Test
    void tryReserveDuplicateIsIdempotent() {
        var existing = new ReservationDO();
        existing.setTxKey("tx-1");
        existing.setState("TRIED");
        when(reservationMapper.findByTxKey("tx-1")).thenReturn(Optional.of(existing));

        CommandResult result = service.tryReserve("tx-1", "SKU-1", 30);
        assertTrue(result.accepted());
        verify(repository, never()).save(any());
    }

    @Test
    void tryReserveOversellReturnsNotRetryable() {
        when(reservationMapper.findByTxKey("tx-2")).thenReturn(Optional.empty());
        var inv = Inventory.create("SKU-1", 50);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));

        CommandResult result = service.tryReserve("tx-2", "SKU-1", 100);
        assertFalse(result.accepted());
        assertFalse(result.retryable());
    }

    @Test
    void confirmIsIdempotent() {
        var res = new ReservationDO();
        res.setTxKey("tx-1");
        res.setSku("SKU-1");
        res.setQty(30);
        res.setState("CONFIRMED");
        when(reservationMapper.findByTxKey("tx-1")).thenReturn(Optional.of(res));

        service.confirm("tx-1");
        verify(repository, never()).save(any());
    }

    @Test
    void cancelReleasesReserved() {
        var res = new ReservationDO();
        res.setTxKey("tx-1");
        res.setSku("SKU-1");
        res.setQty(30);
        res.setState("TRIED");
        when(reservationMapper.findByTxKey("tx-1")).thenReturn(Optional.of(res));
        var inv = Inventory.reconstitute("SKU-1", 100, 30, 1);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));

        service.cancel("tx-1");
        verify(reservationMapper).updateState("tx-1", "CANCELLED");
        verify(repository).save(any());
    }

    @Test
    void emptyRollbackRecordsCancelled() {
        when(reservationMapper.findByTxKey("tx-new")).thenReturn(Optional.empty());

        service.cancel("tx-new");
        verify(reservationMapper).insert("tx-new", "UNKNOWN", 0, "CANCELLED");
    }

    @Test
    void tryAfterEmptyRollbackIsRejected() {
        // Empty rollback recorded as CANCELLED
        var cancelled = new ReservationDO();
        cancelled.setTxKey("tx-1");
        cancelled.setState("CANCELLED");
        when(reservationMapper.findByTxKey("tx-1")).thenReturn(Optional.of(cancelled));

        // Try arrives late — idempotent return (already "processed")
        CommandResult result = service.tryReserve("tx-1", "SKU-1", 30);
        assertTrue(result.accepted()); // idempotent no-op
        verify(repository, never()).save(any());
    }
}