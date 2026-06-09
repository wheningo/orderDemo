package com.example.orderdemo.domain.inventory;

import java.util.Optional;

public interface InventoryRepository {
    Optional<Inventory> findBySku(String sku);
    void save(Inventory inventory);
}