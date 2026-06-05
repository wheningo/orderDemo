package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.order.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void save(Order order) {
        if (order.id() == null) {
            // new order — insert and assign generated id
            OrderDO do_ = toDataObject(order);
            orderMapper.insert(do_);
            order.assignId(new OrderId(do_.getId()));
        } else {
            // existing order — optimistic lock update
            OrderDO do_ = toDataObject(order);
            int updated = orderMapper.updateWithOptimisticLock(do_);
            if (updated == 0) {
                throw new InvariantViolationException(order.id(), "optimistic-lock",
                        "Concurrent modification detected for order: " + order.id().value());
            }
        }
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return orderMapper.findById(id.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return orderMapper.existsByIdempotencyKey(idempotencyKey);
    }

    // --- mapping helpers ---

    private OrderDO toDataObject(Order order) {
        var do_ = new OrderDO();
        if (order.id() != null) {
            do_.setId(order.id().value());
        }
        do_.setProductName(order.productName());
        do_.setQuantity(order.quantity());
        do_.setState(order.state().description());
        do_.setVersion(order.version());
        do_.setIdempotencyKey(order.idempotencyKey());
        return do_;
    }

    private Order toDomain(OrderDO do_) {
        return Order.reconstitute(
                new OrderId(do_.getId()),
                do_.getProductName(),
                do_.getQuantity(),
                OrderState.fromString(do_.getState()),
                do_.getVersion(),
                do_.getIdempotencyKey()
        );
    }
}