package com.example.orderdemo.infrastructure.persistence;

import com.example.orderdemo.domain.inventory.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryMapper mapper;

    public InventoryRepositoryImpl(InventoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Inventory> findBySku(String sku) {
        return mapper.findBySku(sku).map(this::toDomain);
    }

    @Override
    public void save(Inventory inventory) {
        InventoryDO d = toDataObject(inventory);
        int updated = mapper.updateWithCas(d);
        if (updated == 0) {
            throw new OptimisticLockConflictException(inventory.sku());
        }
    }

    private Inventory toDomain(InventoryDO d) {
        return Inventory.reconstitute(d.getSku(), d.getTotal(), d.getReserved(), d.getVersion());
    }

    private InventoryDO toDataObject(Inventory inv) {
        var d = new InventoryDO();
        d.setSku(inv.sku());
        d.setTotal(inv.total());
        d.setReserved(inv.reserved());
        d.setVersion(inv.version());
        return d;
    }
}