package com.example.riskcontrol.domain;

public record RiskDecision(
    Decision decision,
    String reason,
    String ruleId
) {
    public enum Decision {
        PASS,
        REJECT,
        PENDING_REVIEW
    }

    public static RiskDecision pass() {
        return new RiskDecision(Decision.PASS, null, null);
    }

    public static RiskDecision reject(String reason, String ruleId) {
        return new RiskDecision(Decision.REJECT, reason, ruleId);
    }

    public static RiskDecision pendingReview(String reason, String ruleId) {
        return new RiskDecision(Decision.PENDING_REVIEW, reason, ruleId);
    }
}