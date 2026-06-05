package com.example.orderdemo.domain.hotrank;

public record BoostExposureCommand(
    String targetContentId,
    int weight,
    String region,
    String idempotencyKey,
    String decisionSource
) {
    public BoostExposureCommand {
        if (weight < 1 || weight > 100) {
            throw new IllegalArgumentException("Weight must be between 1 and 100, got: " + weight);
        }
    }
}