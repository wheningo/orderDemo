package com.example.orderdemo.model;

/**
 * @author whening
 * @description
 * @date 2025/3/13 09:45
 * @since 1.0
 **/
public final class Confirmed implements OrderState {
    @Override
    public String getDescription() {
        return "CONFIRMED";
    }
}
 
