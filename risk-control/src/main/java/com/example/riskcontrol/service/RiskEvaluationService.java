package com.example.riskcontrol.service;

import com.example.riskcontrol.domain.RiskDecision;
import com.example.riskcontrol.domain.RiskEvaluationRequest;
import com.example.riskcontrol.engine.RiskFact;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final KieContainer kieContainer;
    private final DataEnrichmentService dataEnrichmentService;
    private final ReviewService reviewService;

    public RiskEvaluationService(KieContainer kieContainer, DataEnrichmentService dataEnrichmentService, ReviewService reviewService) {
        this.kieContainer = kieContainer;
        this.dataEnrichmentService = dataEnrichmentService;
        this.reviewService = reviewService;
    }

    public RiskDecision evaluate(RiskEvaluationRequest request) {
        // Step 1: Enrich data (reserved — currently no-op)
        var enriched = dataEnrichmentService.enrich(request);

        // Step 2: Build fact and fire rules
        RiskFact fact = new RiskFact(
                request.commandType(),
                request.targetId(),
                request.region(),
                request.agentId(),
                request.riskTier(),
                request.amount(),
                request.frequencyLastMinute()
        );

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(fact);
            session.fireAllRules();
        } finally {
            session.dispose();
        }

        RiskDecision decision = switch (fact.getDecision()) {
            case "REJECT" -> RiskDecision.reject(fact.getReason(), fact.getRuleId());
            case "PENDING_REVIEW" -> {
                // Step 3: Trigger async review (reserved — currently logs)
                reviewService.submitForReview(request, fact.getReason(), fact.getRuleId());
                yield RiskDecision.pendingReview(fact.getReason(), fact.getRuleId());
            }
            default -> RiskDecision.pass();
        };

        log.info("Risk evaluation: cmd={}, decision={}, rule={}", request.commandType(), decision.decision(), decision.ruleId());
        return decision;
    }
}