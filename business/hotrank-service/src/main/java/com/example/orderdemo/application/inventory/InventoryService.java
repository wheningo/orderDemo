package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_CAS_RETRIES = 3;

    private final InventoryRepository repository;
    private final TransactionTemplate txTemplate;

    public InventoryService(InventoryRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public CommandResult tryReserve(String sku, int qty) {
        for (int attempt = 1; attempt <= MAX_CAS_RETRIES; attempt++) {
            CommandResult result = txTemplate.execute(status -> doReserveInTx(sku, qty));
            if (result != null && !result.retryable()) {
                return result;
            }
            log.info("CAS retry {}/{} for sku={}", attempt, MAX_CAS_RETRIES, sku);
        }
        log.warn("CAS retries exhausted for sku={}, qty={}", sku, qty);
        return CommandResult.conflictRetryable();
    }

    private CommandResult doReserveInTx(String sku, int qty) {
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
            return CommandResult.conflictRetryable();
        }
    }
}