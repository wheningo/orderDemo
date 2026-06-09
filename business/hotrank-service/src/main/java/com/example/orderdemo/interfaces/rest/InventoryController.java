package com.example.orderdemo.interfaces.rest;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.application.inventory.InventoryTccService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryTccService inventoryTccService;

    public InventoryController(InventoryTccService inventoryTccService) {
        this.inventoryTccService = inventoryTccService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<CommandResult> reserve(@RequestBody Map<String, Object> body) {
        String sku = (String) body.get("sku");
        int qty = ((Number) body.get("qty")).intValue();
        String txKey = (String) body.get("txKey");

        CommandResult result = inventoryTccService.tryReserve(txKey, sku, qty);
        return ResponseEntity.ok(result);
    }
}