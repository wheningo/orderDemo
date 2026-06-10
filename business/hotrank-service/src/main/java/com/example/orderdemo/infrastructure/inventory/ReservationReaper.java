package com.example.orderdemo.infrastructure.inventory;

import com.example.orderdemo.application.inventory.InventoryTccService;
import com.example.orderdemo.infrastructure.persistence.ReservationDO;
import com.example.orderdemo.infrastructure.persistence.ReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Disaster-backup reaper: catches reservations that RocketMQ timeout guard missed
 * (broker failure, message loss). Primary timeout mechanism is the per-reservation
 * RocketMQ scheduled message fired at precise deliverTimeMillis.
 */
@Component
public class ReservationReaper {

    private static final Logger log = LoggerFactory.getLogger(ReservationReaper.class);
    private static final Duration TIMEOUT = Duration.ofHours(1);

    private final ReservationMapper reservationMapper;
    private final InventoryTccService inventoryTccService;

    public ReservationReaper(ReservationMapper reservationMapper, InventoryTccService inventoryTccService) {
        this.reservationMapper = reservationMapper;
        this.inventoryTccService = inventoryTccService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void reapStale() {
        Instant before = Instant.now().minus(TIMEOUT);
        List<ReservationDO> stale = reservationMapper.findStaleTried(before);
        for (ReservationDO r : stale) {
            try {
                inventoryTccService.cancel(r.getTxKey());
                log.warn("Disaster reaper: cancelled stale reservation txKey={}, sku={}, qty={}", r.getTxKey(), r.getSku(), r.getQty());
            } catch (Exception e) {
                log.error("Disaster reaper failed for txKey={}", r.getTxKey(), e);
            }
        }
    }
}