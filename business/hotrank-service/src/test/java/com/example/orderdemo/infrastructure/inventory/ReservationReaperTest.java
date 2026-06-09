package com.example.orderdemo.infrastructure.inventory;

import com.example.orderdemo.application.inventory.InventoryTccService;
import com.example.orderdemo.infrastructure.persistence.ReservationDO;
import com.example.orderdemo.infrastructure.persistence.ReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class ReservationReaperTest {

    private ReservationMapper reservationMapper;
    private InventoryTccService inventoryTccService;
    private ReservationReaper reaper;

    @BeforeEach
    void setUp() {
        reservationMapper = mock(ReservationMapper.class);
        inventoryTccService = mock(InventoryTccService.class);
        reaper = new ReservationReaper(reservationMapper, inventoryTccService);
    }

    @Test
    void reapsStaleReservations() {
        var stale = new ReservationDO();
        stale.setTxKey("tx-stale");
        stale.setSku("SKU-1");
        stale.setQty(20);
        stale.setState("TRIED");
        stale.setCreatedAt(Instant.now().minusSeconds(3600));

        when(reservationMapper.findStaleTried(any(Instant.class))).thenReturn(List.of(stale));

        reaper.reapStale();

        verify(inventoryTccService).cancel("tx-stale");
    }

    @Test
    void noStaleReservationsDoesNothing() {
        when(reservationMapper.findStaleTried(any(Instant.class))).thenReturn(List.of());

        reaper.reapStale();

        verify(inventoryTccService, never()).cancel(anyString());
    }

    @Test
    void continuesOnFailure() {
        var stale1 = new ReservationDO();
        stale1.setTxKey("tx-1");
        stale1.setSku("SKU-1");
        stale1.setQty(10);

        var stale2 = new ReservationDO();
        stale2.setTxKey("tx-2");
        stale2.setSku("SKU-2");
        stale2.setQty(20);

        when(reservationMapper.findStaleTried(any(Instant.class))).thenReturn(List.of(stale1, stale2));
        doThrow(new RuntimeException("db error")).when(inventoryTccService).cancel("tx-1");

        reaper.reapStale();

        verify(inventoryTccService).cancel("tx-1");
        verify(inventoryTccService).cancel("tx-2");
    }
}