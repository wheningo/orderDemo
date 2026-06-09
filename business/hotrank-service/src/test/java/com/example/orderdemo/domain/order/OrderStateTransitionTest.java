package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderStateTransitionTest {

    @Test
    void allStatesHaveCorrectDescription() {
        assertEquals("CREATED", OrderState.Created.INSTANCE.description());
        assertEquals("CONFIRMED", OrderState.Confirmed.INSTANCE.description());
        assertEquals("CLOSED", OrderState.Closed.INSTANCE.description());
        assertEquals("CANCELLED", OrderState.Cancelled.INSTANCE.description());
        assertEquals("PENDING", OrderState.Pending.INSTANCE.description());
    }

    @Test
    void fromStringParsesValidStates() {
        assertEquals(OrderState.Created.INSTANCE, OrderState.fromString("CREATED"));
        assertEquals(OrderState.Confirmed.INSTANCE, OrderState.fromString("CONFIRMED"));
        assertEquals(OrderState.Closed.INSTANCE, OrderState.fromString("CLOSED"));
        assertEquals(OrderState.Cancelled.INSTANCE, OrderState.fromString("CANCELLED"));
        assertEquals(OrderState.Pending.INSTANCE, OrderState.fromString("PENDING"));
    }

    @Test
    void fromStringThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> OrderState.fromString("INVALID"));
    }

    @Test
    void patternMatchingIsExhaustive() {
        OrderState state = OrderState.Created.INSTANCE;
        String result = switch (state) {
            case OrderState.Created s -> "created";
            case OrderState.Confirmed s -> "confirmed";
            case OrderState.Closed s -> "closed";
            case OrderState.Cancelled s -> "cancelled";
            case OrderState.Pending s -> "pending";
        };
        assertEquals("created", result);
    }
}
