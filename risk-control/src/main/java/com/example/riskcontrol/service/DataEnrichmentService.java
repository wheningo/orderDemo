package com.example.riskcontrol.service;

import com.example.riskcontrol.domain.RiskEvaluationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reserved interface for third-party data enrichment.
 * Future: call external data services (credit scoring, blacklist, user profile).
 * Currently: no-op passthrough.
 */
@Service
public class DataEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(DataEnrichmentService.class);

    /**
     * Enrich the evaluation request with external data.
     * @return enriched request (currently returns input unchanged)
     */
    public RiskEvaluationRequest enrich(RiskEvaluationRequest request) {
        // TODO: Call external data services here
        // Examples:
        //   - Python data service for user risk profile
        //   - Credit scoring API
        //   - Real-time blacklist check
        //   - Historical behavior aggregation
        log.debug("Data enrichment: no-op (third-party sources not yet integrated)");
        return request;
    }
}