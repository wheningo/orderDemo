package com.example.orderdemo;

import com.example.orderdemo.application.order.*;
import com.example.orderdemo.domain.order.*;
import com.example.orderdemo.infrastructure.persistence.OrderRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration smoke test.
 * Uses H2 (via test application.yml) and mocks KafkaTemplate.
 */
@SpringBootTest
class OrderLifecycleSmokeTest {

    @MockBean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    OrderApplicationService applicationService;

    @Autowired
    OrderQueryService queryService;

    @Autowired
    OrderRepositoryImpl orderRepository;

    // stub kafka before each test via the MockBean
    private void stubKafka() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void fullLifecycle_createThenClose() {
        stubKafka();

        // Place
        var id = applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Espresso", 2, "smoke-k1"));
        assertNotNull(id);

        var order = queryService.getById(id);
        assertInstanceOf(OrderState.Created.class, order.state());
        assertEquals("Espresso", order.productName());
        assertEquals(2, order.quantity());

        // Close
        applicationService.closeOrder(
                new OrderCommand.CloseOrder(id, "test-timeout", "smoke-k2"));

        var closed = queryService.getById(id);
        assertInstanceOf(OrderState.Closed.class, closed.state());
    }

    @Test
    void fullLifecycle_createThenConfirm() {
        stubKafka();

        var id = applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Latte", 1, "smoke-k3"));

        applicationService.confirmOrder(new OrderCommand.ConfirmOrder(id, "smoke-k4"));

        assertInstanceOf(OrderState.Confirmed.class, queryService.getById(id).state());
    }

    @Test
    void fullLifecycle_createThenCancel() {
        stubKafka();

        var id = applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Tea", 1, "smoke-k5"));

        applicationService.cancelOrder(
                new OrderCommand.CancelOrder(id, "changed mind", "smoke-k6"));

        assertInstanceOf(OrderState.Cancelled.class, queryService.getById(id).state());
    }

    @Test
    void invariant_cannotCloseConfirmedOrder() {
        stubKafka();

        var id = applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Mocha", 1, "smoke-k7"));
        applicationService.confirmOrder(new OrderCommand.ConfirmOrder(id, "smoke-k8"));

        var ex = assertThrows(InvariantViolationException.class, () ->
                applicationService.closeOrder(
                        new OrderCommand.CloseOrder(id, "late-timeout", "smoke-k9")));

        assertTrue(ex.getMessage().contains("Cannot close"));
        assertEquals("state-transition", ex.invariant());
    }

    @Test
    void invariant_cannotCancelConfirmedOrder() {
        stubKafka();

        var id = applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Brew", 1, "smoke-k10"));
        applicationService.confirmOrder(new OrderCommand.ConfirmOrder(id, "smoke-k11"));

        assertThrows(InvariantViolationException.class, () ->
                applicationService.cancelOrder(
                        new OrderCommand.CancelOrder(id, "too late", "smoke-k12")));
    }

    @Test
    void idempotency_duplicatePlaceOrderThrows() {
        stubKafka();

        applicationService.placeOrder(
                new OrderCommand.PlaceOrder("Coffee", 1, "smoke-idem-1"));

        var ex = assertThrows(DuplicateCommandException.class, () ->
                applicationService.placeOrder(
                        new OrderCommand.PlaceOrder("Coffee", 1, "smoke-idem-1")));

        assertEquals("smoke-idem-1", ex.idempotencyKey());
    }

    @Test
    void queryService_throwsWhenOrderNotFound() {
        assertThrows(OrderNotFoundException.class,
                () -> queryService.getById(new OrderId(999888777L)));
    }
}