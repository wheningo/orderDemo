package com.example.orderdemo.application.order;

public class OversellRejectedException extends RuntimeException {
    public OversellRejectedException(String reason) {
        super(reason);
    }
}