package com.example.orderdemo.interfaces.rest;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.application.order.ScheduleCommandService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/schedule")
@ConditionalOnBean(ScheduleCommandService.class)
public class ScheduleController {

    private final ScheduleCommandService scheduleCommandService;

    public ScheduleController(ScheduleCommandService scheduleCommandService) {
        this.scheduleCommandService = scheduleCommandService;
    }

    @PostMapping("/close-order")
    public ResponseEntity<CommandResult> scheduleCloseOrder(@RequestBody Map<String, Object> body) {
        String orderId = (String) body.get("orderId");
        String reason = (String) body.getOrDefault("reason", "timeout");
        int delayMinutes = ((Number) body.getOrDefault("delayMinutes", 5)).intValue();
        String idempotencyKey = (String) body.get("idempotencyKey");

        CommandResult result = scheduleCommandService.scheduleCloseOrder(orderId, reason, delayMinutes, idempotencyKey);
        return ResponseEntity.ok(result);
    }
}