package com.example.orderdemo.application.order;

public class DuplicateCommandException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateCommandException(String idempotencyKey) {
        super("Duplicate command, idempotencyKey=" + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() { return idempotencyKey; }
}