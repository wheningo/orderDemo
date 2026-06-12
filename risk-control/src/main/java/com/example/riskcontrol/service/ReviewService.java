package com.example.riskcontrol.service;

import com.example.riskcontrol.domain.RiskEvaluationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reserved interface for async review flow.
 * Future: LLM initial screening + human final approval.
 * Currently: logs the review request.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /**
     * Submit a command for async review.
     * Future flow: LLM screening -> human approval queue -> callback to gateway.
     */
    public void submitForReview(RiskEvaluationRequest request, String reason, String ruleId) {
        // TODO: Implement async review flow
        // Steps:
        //   1. Persist review request to DB (pending state)
        //   2. LLM initial screening (auto-approve obvious cases)
        //   3. Route to human reviewer if LLM uncertain
        //   4. Callback to gateway with approval/rejection
        log.info("Review submitted (stub): cmd={}, target={}, reason={}, rule={}",
                request.commandType(), request.targetId(), reason, ruleId);
    }

    /**
     * Query review status by idempotency key.
     * Future: returns PENDING / APPROVED / REJECTED.
     */
    public String queryStatus(String idempotencyKey) {
        // TODO: Query review state from DB
        return "NOT_IMPLEMENTED";
    }
}