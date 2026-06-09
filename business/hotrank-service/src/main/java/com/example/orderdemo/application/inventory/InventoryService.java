package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CommandResult tryReserve(String sku, int qty) {
        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + sku));
        try {
            inventory.reserve(qty);
            repository.save(inventory);
            log.info("Stock reserved: sku={}, qty={}, remaining={}", sku, qty, inventory.available());
            return CommandResult.ok();
        } catch (OversellException e) {
            log.warn("Oversell rejected: {}", e.getMessage());
            return CommandResult.oversellRejected(sku, qty, (int) e.available());
        } catch (OptimisticLockConflictException e) {
            log.warn("CAS conflict for sku={}, retryable", sku);
            return CommandResult.conflictRetryable();
        }
    }
}