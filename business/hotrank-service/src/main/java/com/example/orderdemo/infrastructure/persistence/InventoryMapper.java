package com.example.orderdemo.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
public interface InventoryMapper {

    @Select("SELECT sku, total, reserved, version FROM inventory WHERE sku = #{sku}")
    Optional<InventoryDO> findBySku(String sku);

    @Insert("INSERT INTO inventory (sku, total, reserved, version) VALUES (#{sku}, #{total}, #{reserved}, 0)")
    void insert(InventoryDO inventoryDO);

    @Update("UPDATE inventory SET reserved = #{reserved}, total = #{total}, version = version + 1 WHERE sku = #{sku} AND version = #{version}")
    int updateWithCas(InventoryDO inventoryDO);
}