package com.example.riskcontrol.controller;

import com.example.riskcontrol.domain.RiskDecision;
import com.example.riskcontrol.domain.RiskEvaluationRequest;
import com.example.riskcontrol.service.RiskEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/risk")
public class RiskController {

    private final RiskEvaluationService evaluationService;

    public RiskController(RiskEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<RiskDecision> evaluate(@RequestBody RiskEvaluationRequest request) {
        RiskDecision decision = evaluationService.evaluate(request);
        return ResponseEntity.ok(decision);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\"}");
    }
}