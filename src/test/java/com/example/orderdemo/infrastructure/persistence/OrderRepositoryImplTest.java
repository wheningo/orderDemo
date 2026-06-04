package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.order.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderRepositoryImpl.class)
class OrderRepositoryImplTest {

    @Autowired
    private OrderRepositoryImpl repository;

    @Test
    void saveNewOrderAssignsId() {
        var order = Order.create(new OrderCommand.PlaceOrder("Tea", 2, "idem-001"));

        repository.save(order);

        assertNotNull(order.id());
        assertNotNull(order.id().value());
    }

    @Test
    void findByIdReturnsReconstitutedOrder() {
        var order = Order.create(new OrderCommand.PlaceOrder("Latte", 1, "idem-002"));
        repository.save(order);

        var found = repository.findById(order.id());

        assertTrue(found.isPresent());
        assertEquals("Latte", found.get().productName());
        assertEquals(1, found.get().quantity());
        assertInstanceOf(OrderState.Created.class, found.get().state());
        assertEquals("idem-002", found.get().idempotencyKey());
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findById(new OrderId(999999L)).isEmpty());
    }

    @Test
    void existsByIdempotencyKeyReturnsTrueWhenPresent() {
        var order = Order.create(new OrderCommand.PlaceOrder("Espresso", 3, "idem-003"));
        repository.save(order);

        assertTrue(repository.existsByIdempotencyKey("idem-003"));
        assertFalse(repository.existsByIdempotencyKey("idem-999"));
    }

    @Test
    void saveExistingOrderUpdatesState() {
        var order = Order.create(new OrderCommand.PlaceOrder("Mocha", 1, "idem-004"));
        repository.save(order);

        order.confirm(new OrderCommand.ConfirmOrder(order.id(), "idem-005"));
        repository.save(order);

        var found = repository.findById(order.id()).orElseThrow();
        assertInstanceOf(OrderState.Confirmed.class, found.state());
    }
}