package com.example.orderdemo.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface ReservationMapper {

    @Insert("INSERT INTO reservation (tx_key, sku, qty, state, created_at) VALUES (#{txKey}, #{sku}, #{qty}, #{state}, NOW())")
    void insert(@Param("txKey") String txKey, @Param("sku") String sku, @Param("qty") int qty, @Param("state") String state);

    @Select("SELECT tx_key, sku, qty, state, created_at FROM reservation WHERE tx_key = #{txKey}")
    @Results({
        @Result(property = "txKey", column = "tx_key"),
        @Result(property = "sku", column = "sku"),
        @Result(property = "qty", column = "qty"),
        @Result(property = "state", column = "state"),
        @Result(property = "createdAt", column = "created_at")
    })
    Optional<ReservationDO> findByTxKey(String txKey);

    @Update("UPDATE reservation SET state = #{state} WHERE tx_key = #{txKey} AND state = 'TRIED'")
    int updateState(@Param("txKey") String txKey, @Param("state") String state);
}