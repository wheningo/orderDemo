package com.example.orderdemo.model;

import jakarta.persistence.AttributeConverter;

/**
 * @author whening
 * @description
 * @date 2025/3/13 09:45
 * @since 1.0
 **/
public class OrderStateConverter implements AttributeConverter<OrderState, String> {
    @Override
    public String convertToDatabaseColumn(OrderState state) {
        return state.getDescription();
    }

    @Override
    public OrderState convertToEntityAttribute(String dbData) {
        return switch (dbData) {
            case "CREATED" -> new Created();
            case "CONFIRMED" -> new Confirmed();
            case "CANCELLED" -> new Cancelled();
            default -> throw new IllegalArgumentException("Unknown state: " + dbData);
        };
    }
}
 
