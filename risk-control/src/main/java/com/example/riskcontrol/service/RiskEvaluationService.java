package com.example.riskcontrol.service;

import com.example.riskcontrol.domain.RiskDecision;
import com.example.riskcontrol.domain.RiskEvaluationRequest;
import com.example.riskcontrol.engine.RiskFact;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class RiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final AtomicReference<KieContainer> kieContainerRef;
    private final DataEnrichmentService dataEnrichmentService;
    private final ReviewService reviewService;

    public RiskEvaluationService(AtomicReference<KieContainer> kieContainerRef, DataEnrichmentService dataEnrichmentService, ReviewService reviewService) {
        this.kieContainerRef = kieContainerRef;
        this.dataEnrichmentService = dataEnrichmentService;
        this.reviewService = reviewService;
    }

    public RiskDecision evaluate(RiskEvaluationRequest request) {
        var enriched = dataEnrichmentService.enrich(request);

        RiskFact fact = new RiskFact(
                request.commandType(),
                request.targetId(),
                request.region(),
                request.agentId(),
                request.riskTier(),
                request.amount(),
                request.frequencyLastMinute()
        );

        KieContainer container = kieContainerRef.get();
        KieSession session = container.newKieSession();
        try {
            session.insert(fact);
            session.fireAllRules();
        } finally {
            session.dispose();
        }

        RiskDecision decision = switch (fact.getDecision()) {
            case "REJECT" -> RiskDecision.reject(fact.getReason(), fact.getRuleId());
            case "PENDING_REVIEW" -> {
                reviewService.submitForReview(request, fact.getReason(), fact.getRuleId());
                yield RiskDecision.pendingReview(fact.getReason(), fact.getRuleId());
            }
            default -> RiskDecision.pass();
        };

        log.info("Risk evaluation: cmd={}, decision={}, rule={}", request.commandType(), decision.decision(), decision.ruleId());
        return decision;
    }
}