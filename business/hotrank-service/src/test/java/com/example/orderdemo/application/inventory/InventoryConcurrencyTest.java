package com.example.orderdemo.application.inventory;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.infrastructure.persistence.InventoryMapper;
import com.example.orderdemo.infrastructure.persistence.InventoryDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Sql(statements = {
    "DELETE FROM inventory",
    "INSERT INTO inventory (sku, total, reserved, version) VALUES ('SKU-RACE', 100, 0, 0)"
})
class InventoryConcurrencyTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryMapper inventoryMapper;
    @MockBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @Test
    void concurrentReservesNeverOversell() throws Exception {
        int threadCount = 50;
        int qtyPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger oversellCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                CommandResult result = inventoryService.tryReserve("SKU-RACE", qtyPerThread);
                if (result.accepted()) {
                    successCount.incrementAndGet();
                } else if (result.retryable()) {
                    conflictCount.incrementAndGet();
                } else {
                    oversellCount.incrementAndGet();
                }
            }));
        }

        startGate.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<?> f : futures) {
            f.get();
        }

        // Core property: successful reservations × qty ≤ total
        int totalReserved = successCount.get() * qtyPerThread;
        assertTrue(totalReserved <= 100,
                "Oversell detected! reserved=%d > total=100".formatted(totalReserved));

        assertTrue(successCount.get() >= 1, "Expected at least 1 success");

        // Verify DB state
        InventoryDO inv = inventoryMapper.findBySku("SKU-RACE").orElseThrow();
        assertTrue(inv.getReserved() >= 0, "reserved must be non-negative");
        assertTrue(inv.getReserved() <= inv.getTotal(), "reserved must not exceed total");
        assertEquals(totalReserved, inv.getReserved(),
                "DB reserved must match successful count × qty");

        System.out.printf("Concurrency test: success=%d, oversell_rejected=%d, conflict=%d, db_reserved=%d%n",
                successCount.get(), oversellCount.get(), conflictCount.get(), inv.getReserved());
    }

    @Test
    void concurrentReservesExactlyExhaustStock() throws Exception {
        int threadCount = 10;
        int qtyPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) { return; }
                CommandResult result = inventoryService.tryReserve("SKU-RACE", qtyPerThread);
                if (result.accepted()) {
                    successCount.incrementAndGet();
                }
            });
        }

        startGate.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        InventoryDO inv = inventoryMapper.findBySku("SKU-RACE").orElseThrow();
        assertTrue(inv.getReserved() <= 100);
        assertEquals(successCount.get() * qtyPerThread, inv.getReserved());
    }
}