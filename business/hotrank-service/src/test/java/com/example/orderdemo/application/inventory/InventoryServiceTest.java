package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private InventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        service = new InventoryService(repository);
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
    void tryReserveCasConflictReturnsRetryable() {
        var inv = Inventory.create("SKU-1", 100);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(inv));
        doThrow(new OptimisticLockConflictException("SKU-1")).when(repository).save(any());

        CommandResult result = service.tryReserve("SKU-1", 30);
        assertFalse(result.accepted());
        assertTrue(result.retryable());
    }

    @Test
    void tryReserveUnknownSkuThrows() {
        when(repository.findBySku("NOPE")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.tryReserve("NOPE", 10));
    }
}