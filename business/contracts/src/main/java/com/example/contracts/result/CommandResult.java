package com.example.contracts.result;

public record CommandResult(boolean accepted, String reason, boolean retryable) {

    public static CommandResult ok() {
        return new CommandResult(true, null, false);
    }

    public static CommandResult rejected(String reason, boolean retryable) {
        return new CommandResult(false, reason, retryable);
    }

    public static CommandResult oversellRejected(String sku, int requested, int available) {
        return new CommandResult(false,
                "Oversell rejected: sku=%s, requested=%d, available=%d".formatted(sku, requested, available),
                false);
    }

    public static CommandResult conflictRetryable() {
        return new CommandResult(false, "Concurrent conflict, please retry", true);
    }
}