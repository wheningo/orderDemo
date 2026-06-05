package com.example.orderdemo.domain.order;

import java.util.Objects;

public record OrderId(Long value) {
    public OrderId {
        Objects.requireNonNull(value, "OrderId value must not be null");
    }
}