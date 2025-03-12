package com.example.orderdemo.repository;

import com.example.orderdemo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author whening
 * @description
 * @date 2025/3/12 17:22
 * @since 1.0
 **/
public interface OrderRepository extends JpaRepository<Order, Long> {}


