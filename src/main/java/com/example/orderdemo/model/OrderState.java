package com.example.orderdemo.model;

/**
 * @author whening
 * @description
 * @date 2025/3/12 17:32
 * @since 1.0
 **/
public sealed interface OrderState permits Created, Paid, Shipped {
    String getDescription();
}