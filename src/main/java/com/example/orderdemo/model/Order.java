package com.example.orderdemo.model;
import jakarta.persistence.*;


/**
 * @author whening
 * @description
 * @date 2025/3/12 17:00
 * @since 1.0
 **/
@Entity
public record Order(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id,
        String productName,
        int quantity,
        OrderState state,

        @Version Long version
) {}
 
