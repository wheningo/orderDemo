package com.example.orderdemo.domain.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void reserveSucceedsWhenAvailable() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(30);
        assertEquals(70, inv.available());
        assertEquals(30, inv.reserved());
    }

    @Test
    void reserveThrowsOversellWhenExceedsAvailable() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(80);
        var ex = assertThrows(OversellException.class, () -> inv.reserve(30));
        assertEquals("SKU-1", ex.sku());
        assertEquals(30, ex.requested());
        assertEquals(20, ex.available());
    }

    @Test
    void reserveExactlyAvailableSucceeds() {
        var inv = Inventory.create("SKU-1", 50);
        inv.reserve(50);
        assertEquals(0, inv.available());
    }

    @Test
    void confirmReducesTotalAndReserved() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(30);
        inv.confirm(30);
        assertEquals(70, inv.total());
        assertEquals(0, inv.reserved());
        assertEquals(70, inv.available());
    }

    @Test
    void cancelReleasesReserved() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(30);
        inv.cancel(30);
        assertEquals(100, inv.total());
        assertEquals(0, inv.reserved());
        assertEquals(100, inv.available());
    }

    @Test
    void invariantAlwaysHolds() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(60);
        assertEquals(inv.total(), inv.reserved() + inv.available());
        inv.confirm(20);
        assertEquals(inv.total(), inv.reserved() + inv.available());
        inv.cancel(10);
        assertEquals(inv.total(), inv.reserved() + inv.available());
    }

    @Test
    void cannotConfirmMoreThanReserved() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(20);
        assertThrows(IllegalStateException.class, () -> inv.confirm(30));
    }

    @Test
    void cannotCancelMoreThanReserved() {
        var inv = Inventory.create("SKU-1", 100);
        inv.reserve(20);
        assertThrows(IllegalStateException.class, () -> inv.cancel(30));
    }
}