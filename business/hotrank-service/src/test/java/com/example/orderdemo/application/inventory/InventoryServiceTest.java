package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private InventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new InventoryService(repository, txManager);
    }

    @Test
    void tryReserveSucceeds() {
        var inv = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));
        doNothing().when(repository).save(any());

        CommandResult result = service.tryReserve("SKU-1", 30);
        assertTrue(result.accepted());
        assertFalse(result.retryable());
    }

    @Test
    void tryReserveOversellReturnsNotRetryable() {
        var inv = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));

        CommandResult result = service.tryReserve("SKU-1", 150);
        assertFalse(result.accepted());
        assertFalse(result.retryable());
        assertTrue(result.reason().contains("Oversell"));
    }

    @Test
    void tryReserveCasConflictRetriesAndExhausts() {
        var inv = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));
        doThrow(new OptimisticLockConflictException("SKU-1")).when(repository).save(any());

        CommandResult result = service.tryReserve("SKU-1", 30);
        assertFalse(result.accepted());
        assertTrue(result.retryable());
        // Verify it retried 3 times (MAX_CAS_RETRIES)
        verify(repository, times(3)).save(any());
    }

    @Test
    void tryReserveUnknownSkuThrows() {
        when(repository.findBySku("NOPE")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.tryReserve("NOPE", 10));
    }

    @Test
    void tryReserveCasConflictSucceedsOnRetry() {
        var inv1 = Inventory.create("SKU-1", 100);
        var inv2 = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1"))
                .thenReturn(Optional.of(inv1))
                .thenReturn(Optional.of(inv2));
        doThrow(new OptimisticLockConflictException("SKU-1"))
                .doNothing()
                .when(repository).save(any());

        CommandResult result = service.tryReserve("SKU-1", 30);
        assertTrue(result.accepted());
        verify(repository, times(2)).save(any());
    }
}