package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrderApplicationServiceTest {

    // --- minimal in-memory stubs ---

    private static class InMemoryOrderRepository implements OrderRepository {
        private final java.util.Map<Long, Order> store = new java.util.HashMap<>();
        private long seq = 1;

        @Override
        public void save(Order order) {
            if (order.id() == null) {
                order.assignId(new OrderId(seq++));
            }
            store.put(order.id().value(), order);
        }

        @Override
        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public boolean existsByIdempotencyKey(String key) {
            return store.values().stream()
                    .anyMatch(o -> key.equals(o.idempotencyKey()));
        }
    }

    private static class CapturingPublisher implements OrderEventPublisher {
        final List<OrderEvent> published = new ArrayList<>();

        @Override
        public void publish(OrderEvent event) {
            published.add(event);
        }
    }

    private InMemoryOrderRepository repository;
    private CapturingPublisher publisher;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
        publisher  = new CapturingPublisher();
        service    = new OrderApplicationService(repository, publisher);
    }

    @Test
    void placeOrderPersistsAndPublishesEvent() {
        var id = service.placeOrder(new OrderCommand.PlaceOrder("Coffee", 2, "k1"));

        assertNotNull(id);
        var order = repository.findById(id).orElseThrow();
        assertInstanceOf(OrderState.Created.class, order.state());

        assertEquals(1, publisher.published.size());
        assertInstanceOf(OrderEvent.OrderCreated.class, publisher.published.getFirst());
    }

    @Test
    void placeOrderThrowsOnDuplicateKey() {
        service.placeOrder(new OrderCommand.PlaceOrder("Coffee", 2, "k-dup"));

        assertThrows(DuplicateCommandException.class,
                () -> service.placeOrder(new OrderCommand.PlaceOrder("Coffee", 2, "k-dup")));
    }

    @Test
    void confirmOrderTransitionsStateAndPublishes() {
        var id = service.placeOrder(new OrderCommand.PlaceOrder("Tea", 1, "k2"));
        publisher.published.clear();

        service.confirmOrder(new OrderCommand.ConfirmOrder(id, "k3"));

        var order = repository.findById(id).orElseThrow();
        assertInstanceOf(OrderState.Confirmed.class, order.state());
        assertEquals(1, publisher.published.size());
        assertInstanceOf(OrderEvent.OrderConfirmed.class, publisher.published.getFirst());
    }

    @Test
    void closeOrderFromCreatedState() {
        var id = service.placeOrder(new OrderCommand.PlaceOrder("Latte", 1, "k4"));
        publisher.published.clear();

        service.closeOrder(new OrderCommand.CloseOrder(id, "timeout", "k5"));

        assertInstanceOf(OrderState.Closed.class, repository.findById(id).orElseThrow().state());
        assertInstanceOf(OrderEvent.OrderClosed.class, publisher.published.getFirst());
    }

    @Test
    void cancelOrderFromCreatedState() {
        var id = service.placeOrder(new OrderCommand.PlaceOrder("Juice", 1, "k6"));
        publisher.published.clear();

        service.cancelOrder(new OrderCommand.CancelOrder(id, "changed mind", "k7"));

        assertInstanceOf(OrderState.Cancelled.class, repository.findById(id).orElseThrow().state());
        assertInstanceOf(OrderEvent.OrderCancelled.class, publisher.published.getFirst());
    }

    @Test
    void confirmThrowsWhenOrderNotFound() {
        var cmd = new OrderCommand.ConfirmOrder(new OrderId(9999L), "k8");
        assertThrows(OrderNotFoundException.class, () -> service.confirmOrder(cmd));
    }

    @Test
    void eventsAreClearedAfterPublish() {
        var id = service.placeOrder(new OrderCommand.PlaceOrder("Brew", 1, "k9"));
        var order = repository.findById(id).orElseThrow();
        assertTrue(order.domainEvents().isEmpty(), "Events should be cleared after publish");
    }
}