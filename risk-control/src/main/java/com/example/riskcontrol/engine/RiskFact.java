package com.example.riskcontrol.engine;

public class RiskFact {

    private String commandType;
    private String targetId;
    private String region;
    private String agentId;
    private String riskTier;
    private int amount;
    private int frequencyLastMinute;

    // Result fields — written by rules
    private String decision = "PASS";
    private String reason;
    private String ruleId;

    public RiskFact() {}

    public RiskFact(String commandType, String targetId, String region, String agentId,
                    String riskTier, int amount, int frequencyLastMinute) {
        this.commandType = commandType;
        this.targetId = targetId;
        this.region = region;
        this.agentId = agentId;
        this.riskTier = riskTier;
        this.amount = amount;
        this.frequencyLastMinute = frequencyLastMinute;
    }

    // Getters and setters for all fields
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public int getFrequencyLastMinute() { return frequencyLastMinute; }
    public void setFrequencyLastMinute(int frequencyLastMinute) { this.frequencyLastMinute = frequencyLastMinute; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
}