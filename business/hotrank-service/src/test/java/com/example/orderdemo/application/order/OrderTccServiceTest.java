package com.example.orderdemo.application.order;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderTccServiceTest {

    private OrderRepository orderRepository;
    private OrderTccService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        service = new OrderTccService(orderRepository);
    }

    @Test
    void tryCreateMakesPendingOrder() {
        doAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.assignId(new OrderId(1L));
            return null;
        }).when(orderRepository).save(any());

        OrderId id = service.tryCreate("tx-1", "Coffee", 3);
        assertNotNull(id);
        verify(orderRepository).save(argThat(o -> o.state() instanceof OrderState.Pending));
    }

    @Test
    void confirmTransitionsPendingToConfirmed() {
        var order = Order.createPending(new OrderCommand.PlaceOrder("Coffee", 3, "tx-1"));
        order.assignId(new OrderId(1L));
        when(orderRepository.findById(new OrderId(1L))).thenReturn(Optional.of(order));

        service.confirm(new OrderId(1L));
        assertInstanceOf(OrderState.Confirmed.class, order.state());
        verify(orderRepository).save(order);
    }

    @Test
    void cancelTransitionsPendingToCancelled() {
        var order = Order.createPending(new OrderCommand.PlaceOrder("Coffee", 3, "tx-1"));
        order.assignId(new OrderId(1L));
        when(orderRepository.findById(new OrderId(1L))).thenReturn(Optional.of(order));

        service.cancel(new OrderId(1L));
        assertInstanceOf(OrderState.Cancelled.class, order.state());
        verify(orderRepository).save(order);
    }

    @Test
    void confirmIsIdempotentOnNonPending() {
        var order = Order.reconstitute(new OrderId(1L), "Coffee", 3, OrderState.fromString("CONFIRMED"), 1, "tx-1");
        when(orderRepository.findById(new OrderId(1L))).thenReturn(Optional.of(order));

        service.confirm(new OrderId(1L));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelIsIdempotentOnNonPending() {
        var order = Order.reconstitute(new OrderId(1L), "Coffee", 3, OrderState.fromString("CANCELLED"), 1, "tx-1");
        when(orderRepository.findById(new OrderId(1L))).thenReturn(Optional.of(order));

        service.cancel(new OrderId(1L));
        verify(orderRepository, never()).save(any());
    }
}