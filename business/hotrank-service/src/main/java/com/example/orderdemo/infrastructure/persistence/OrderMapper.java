package com.example.orderdemo.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface OrderMapper {

    @Insert("""
            INSERT INTO orders (product_name, quantity, state, version, idempotency_key)
            VALUES (#{productName}, #{quantity}, #{state}, #{version}, #{idempotencyKey})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderDO orderDO);

    @Update("""
            UPDATE orders
            SET state = #{state}, version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int updateWithOptimisticLock(OrderDO orderDO);

    @Select("SELECT id, product_name, quantity, state, version, idempotency_key FROM orders WHERE id = #{id}")
    @Results(id = "orderResultMap", value = {
            @Result(property = "id",             column = "id"),
            @Result(property = "productName",    column = "product_name"),
            @Result(property = "quantity",       column = "quantity"),
            @Result(property = "state",          column = "state"),
            @Result(property = "version",        column = "version"),
            @Result(property = "idempotencyKey", column = "idempotency_key")
    })
    Optional<OrderDO> findById(Long id);

    @Select("SELECT COUNT(1) FROM orders WHERE idempotency_key = #{idempotencyKey}")
    boolean existsByIdempotencyKey(String idempotencyKey);
}