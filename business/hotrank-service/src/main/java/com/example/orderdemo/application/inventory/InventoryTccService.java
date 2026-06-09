package com.example.orderdemo.application.inventory;

import com.example.contracts.event.StockConfirmed;
import com.example.contracts.event.StockReleased;
import com.example.contracts.event.StockReserved;
import com.example.contracts.result.CommandResult;
import com.example.orderdemo.domain.inventory.*;
import com.example.orderdemo.infrastructure.outbox.OutboxWriter;
import com.example.orderdemo.infrastructure.persistence.ReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryTccService {

    private static final Logger log = LoggerFactory.getLogger(InventoryTccService.class);

    private final InventoryRepository repository;
    private final ReservationMapper reservationMapper;
    private final OutboxWriter outboxWriter;

    public InventoryTccService(InventoryRepository repository, ReservationMapper reservationMapper, OutboxWriter outboxWriter) {
        this.repository = repository;
        this.reservationMapper = reservationMapper;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommandResult tryReserve(String txKey, String sku, int qty) {
        var existing = reservationMapper.findByTxKey(txKey);
        if (existing.isPresent()) {
            log.info("Duplicate try ignored: txKey={}", txKey);
            return CommandResult.ok();
        }

        Inventory inventory = repository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + sku));
        try {
            inventory.reserve(qty);
            repository.save(inventory);
            reservationMapper.insert(txKey, sku, qty, "TRIED");
            outboxWriter.write("Inventory", sku, "StockReserved",
                    new StockReserved(UUID.randomUUID().toString(), sku, qty, txKey, Instant.now()));
            log.info("TCC Try: reserved sku={}, qty={}, txKey={}", sku, qty, txKey);
            return CommandResult.ok();
        } catch (OversellException e) {
            log.warn("TCC Try oversell: {}", e.getMessage());
            return CommandResult.oversellRejected(sku, qty, (int) e.available());
        } catch (OptimisticLockConflictException e) {
            log.warn("TCC Try CAS conflict: sku={}", sku);
            return CommandResult.conflictRetryable();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(String txKey) {
        var existing = reservationMapper.findByTxKey(txKey);
        if (existing.isEmpty()) {
            log.warn("Confirm for unknown txKey={}, ignoring", txKey);
            return;
        }
        var res = existing.get();
        if (!"TRIED".equals(res.getState())) {
            log.info("Confirm already processed: txKey={}, state={}", txKey, res.getState());
            return;
        }

        Inventory inventory = repository.findBySku(res.getSku()).orElseThrow();
        inventory.confirm(res.getQty());
        repository.save(inventory);
        reservationMapper.updateState(txKey, "CONFIRMED");
        outboxWriter.write("Inventory", res.getSku(), "StockConfirmed",
                new StockConfirmed(UUID.randomUUID().toString(), res.getSku(), res.getQty(), txKey, Instant.now()));
        log.info("TCC Confirm: sku={}, qty={}, txKey={}", res.getSku(), res.getQty(), txKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(String txKey) {
        var existing = reservationMapper.findByTxKey(txKey);
        if (existing.isEmpty()) {
            log.info("Empty rollback: txKey={}", txKey);
            reservationMapper.insert(txKey, "UNKNOWN", 0, "CANCELLED");
            return;
        }
        var res = existing.get();
        if (!"TRIED".equals(res.getState())) {
            log.info("Cancel already processed: txKey={}, state={}", txKey, res.getState());
            return;
        }

        Inventory inventory = repository.findBySku(res.getSku()).orElseThrow();
        inventory.cancel(res.getQty());
        repository.save(inventory);
        reservationMapper.updateState(txKey, "CANCELLED");
        outboxWriter.write("Inventory", res.getSku(), "StockReleased",
                new StockReleased(UUID.randomUUID().toString(), res.getSku(), res.getQty(), txKey, Instant.now()));
        log.info("TCC Cancel: sku={}, qty={}, txKey={}", res.getSku(), res.getQty(), txKey);
    }
}