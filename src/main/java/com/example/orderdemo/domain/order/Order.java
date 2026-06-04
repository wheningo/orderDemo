package com.example.orderdemo.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {

    private OrderId id;
    private String productName;
    private int quantity;
    private OrderState state;
    private long version;
    private final List<OrderEvent> domainEvents = new ArrayList<>();

    private Order() {}

    public static Order create(OrderCommand.PlaceOrder cmd) {
        var order = new Order();
        order.productName = cmd.productName();
        order.quantity = cmd.quantity();
        order.state = OrderState.Created.INSTANCE;
        order.domainEvents.add(new OrderEvent.OrderCreated(
                order.id, cmd.productName(), cmd.quantity(), Instant.now()));
        return order;
    }

    public void confirm(OrderCommand.ConfirmOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot confirm order in state: " + state.description());
        }
        this.state = OrderState.Confirmed.INSTANCE;
        domainEvents.add(new OrderEvent.OrderConfirmed(id, Instant.now()));
    }

    public void close(OrderCommand.CloseOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot close order in state: " + state.description());
        }
        this.state = OrderState.Closed.INSTANCE;
        domainEvents.add(new OrderEvent.OrderClosed(id, cmd.reason(), Instant.now()));
    }

    public void cancel(OrderCommand.CancelOrder cmd) {
        if (!(state instanceof OrderState.Created)) {
            throw new InvariantViolationException(id, "state-transition",
                    "Cannot cancel order in state: " + state.description());
        }
        this.state = OrderState.Cancelled.INSTANCE;
        domainEvents.add(new OrderEvent.OrderCancelled(id, cmd.reason(), Instant.now()));
    }

    public static Order reconstitute(OrderId id, String productName, int quantity, OrderState state, long version) {
        var order = new Order();
        order.id = id;
        order.productName = productName;
        order.quantity = quantity;
        order.state = state;
        order.version = version;
        return order;
    }

    public void assignId(OrderId id) {
        if (this.id != null) throw new IllegalStateException("Id already assigned");
        this.id = id;
    }

    public OrderId id() { return id; }
    public String productName() { return productName; }
    public int quantity() { return quantity; }
    public OrderState state() { return state; }
    public long version() { return version; }

    public List<OrderEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearEvents() {
        domainEvents.clear();
    }
}