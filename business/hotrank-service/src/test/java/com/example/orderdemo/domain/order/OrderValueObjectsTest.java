package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class OrderValueObjectsTest {

    @Test
    void orderIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new OrderId(null));
    }

    @Test
    void orderIdEquality() {
        assertEquals(new OrderId(1L), new OrderId(1L));
        assertNotEquals(new OrderId(1L), new OrderId(2L));
    }

    @Test
    void placeOrderCommandValidation() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "idem-key-001");
        assertEquals("Coffee", cmd.productName());
        assertEquals(3, cmd.quantity());
        assertEquals("idem-key-001", cmd.idempotencyKey());
    }

    @Test
    void placeOrderCommandRejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", 0, "key"));
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", -1, "key"));
    }

    @Test
    void closeOrderCommandConstruction() {
        var cmd = new OrderCommand.CloseOrder(new OrderId(1L), "timeout", "idem-key-002");
        assertEquals(new OrderId(1L), cmd.orderId());
        assertEquals("timeout", cmd.reason());
    }

    @Test
    void orderCreatedEventCarriesData() {
        var event = new OrderEvent.OrderCreated(new OrderId(1L), "Coffee", 3, Instant.now());
        assertEquals(new OrderId(1L), event.orderId());
        assertEquals("Coffee", event.productName());
    }

    @Test
    void orderClosedEventCarriesReason() {
        var event = new OrderEvent.OrderClosed(new OrderId(1L), "timeout", Instant.now());
        assertEquals("timeout", event.reason());
    }
}