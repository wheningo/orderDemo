package com.example.orderdemo.domain.order;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void createOrderProducesCreatedEvent() {
        var cmd = new OrderCommand.PlaceOrder("Coffee", 3, "key-001");
        var order = Order.create(cmd);

        assertNotNull(order);
        assertEquals("Coffee", order.productName());
        assertEquals(3, order.quantity());
        assertInstanceOf(OrderState.Created.class, order.state());

        List<OrderEvent> events = order.domainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(OrderEvent.OrderCreated.class, events.getFirst());

        var event = (OrderEvent.OrderCreated) events.getFirst();
        assertEquals("Coffee", event.productName());
        assertEquals(3, event.quantity());
    }

    @Test
    void confirmOrderFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        var cmd = new OrderCommand.ConfirmOrder(order.id(), "key-002");
        order.confirm(cmd);

        assertInstanceOf(OrderState.Confirmed.class, order.state());
        assertEquals(1, order.domainEvents().size());
        assertInstanceOf(OrderEvent.OrderConfirmed.class, order.domainEvents().getFirst());
    }

    @Test
    void closeOrderFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        order.close(cmd);

        assertInstanceOf(OrderState.Closed.class, order.state());
        assertEquals(1, order.domainEvents().size());
        var event = (OrderEvent.OrderClosed) order.domainEvents().getFirst();
        assertEquals("timeout", event.reason());
    }

    @Test
    void cannotConfirmAlreadyClosedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.close(new OrderCommand.CloseOrder(order.id(), "timeout", "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.ConfirmOrder(order.id(), "key-003");
        var ex = assertThrows(InvariantViolationException.class, () -> order.confirm(cmd));
        assertTrue(ex.getMessage().contains("Cannot confirm"));
        assertEquals("state-transition", ex.invariant());
    }

    @Test
    void cannotCloseAlreadyConfirmedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        var ex = assertThrows(InvariantViolationException.class, () -> order.close(cmd));
        assertTrue(ex.getMessage().contains("Cannot close"));
    }

    @Test
    void cannotCloseCancelledOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.cancel(new OrderCommand.CancelOrder(order.id(), "user request", "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CloseOrder(order.id(), "timeout", "key-003");
        assertThrows(InvariantViolationException.class, () -> order.close(cmd));
    }

    @Test
    void canCancelFromCreatedState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.clearEvents();

        order.cancel(new OrderCommand.CancelOrder(order.id(), "changed mind", "key-002"));
        assertInstanceOf(OrderState.Cancelled.class, order.state());
    }

    @Test
    void cannotCancelConfirmedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Coffee", 2, "key-001"));
        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "key-002"));
        order.clearEvents();

        var cmd = new OrderCommand.CancelOrder(order.id(), "too late", "key-003");
        assertThrows(InvariantViolationException.class, () -> order.cancel(cmd));
    }

    @Test
    void quantityMustBePositive() {
        assertThrows(IllegalArgumentException.class,
            () -> new OrderCommand.PlaceOrder("Coffee", 0, "key"));
    }
}