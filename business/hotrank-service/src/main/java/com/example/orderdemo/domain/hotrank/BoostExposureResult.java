package com.example.orderdemo.domain.hotrank;

public record BoostExposureResult(boolean accepted, String reason, String idempotencyKey) {
    public static BoostExposureResult accepted(String idempotencyKey) {
        return new BoostExposureResult(true, null, idempotencyKey);
    }

    public static BoostExposureResult rejected(String reason, String idempotencyKey) {
        return new BoostExposureResult(false, reason, idempotencyKey);
    }

    public static BoostExposureResult duplicate(String idempotencyKey) {
        return new BoostExposureResult(true, "duplicate", idempotencyKey);
    }
}