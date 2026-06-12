package com.example.riskcontrol.service;

import com.example.riskcontrol.domain.RiskDecision;
import com.example.riskcontrol.domain.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RiskEvaluationServiceTest {

    @Autowired
    private RiskEvaluationService service;

    @Test
    void normalRequestPasses() {
        var request = new RiskEvaluationRequest(
                "allocate_promo_stock", "SKU-1", "CN", "agent-1", "standard", 50, 5, "key-1");
        RiskDecision decision = service.evaluate(request);
        assertEquals(RiskDecision.Decision.PASS, decision.decision());
    }

    @Test
    void highFrequencyRejected() {
        var request = new RiskEvaluationRequest(
                "allocate_promo_stock", "SKU-1", "CN", "agent-1", "standard", 50, 25, "key-2");
        RiskDecision decision = service.evaluate(request);
        assertEquals(RiskDecision.Decision.REJECT, decision.decision());
        assertEquals("FREQ_LIMIT", decision.ruleId());
    }

    @Test
    void largeAmountPendingReview() {
        var request = new RiskEvaluationRequest(
                "allocate_promo_stock", "SKU-1", "CN", "agent-1", "standard", 600, 5, "key-3");
        RiskDecision decision = service.evaluate(request);
        assertEquals(RiskDecision.Decision.PENDING_REVIEW, decision.decision());
        assertEquals("AMOUNT_REVIEW", decision.ruleId());
    }

    @Test
    void l4TierRequiresApproval() {
        var request = new RiskEvaluationRequest(
                "boost_exposure", "content-1", "CN", "agent-1", "L4", 10, 1, "key-4");
        RiskDecision decision = service.evaluate(request);
        assertEquals(RiskDecision.Decision.PENDING_REVIEW, decision.decision());
        assertEquals("L4_APPROVAL", decision.ruleId());
    }

    @Test
    void unknownRegionRejected() {
        var request = new RiskEvaluationRequest(
                "allocate_promo_stock", "SKU-1", "MARS", "agent-1", "standard", 50, 5, "key-5");
        RiskDecision decision = service.evaluate(request);
        assertEquals(RiskDecision.Decision.REJECT, decision.decision());
        assertEquals("REGION_BLOCK", decision.ruleId());
    }
}