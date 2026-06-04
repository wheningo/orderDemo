package com.example.orderdemo.domain.order;

public sealed interface OrderState {

    String description();

    record Created() implements OrderState {
        static final Created INSTANCE = new Created();
        @Override public String description() { return "CREATED"; }
    }

    record Confirmed() implements OrderState {
        static final Confirmed INSTANCE = new Confirmed();
        @Override public String description() { return "CONFIRMED"; }
    }

    record Closed() implements OrderState {
        static final Closed INSTANCE = new Closed();
        @Override public String description() { return "CLOSED"; }
    }

    record Cancelled() implements OrderState {
        static final Cancelled INSTANCE = new Cancelled();
        @Override public String description() { return "CANCELLED"; }
    }

    static OrderState fromString(String value) {
        return switch (value) {
            case "CREATED" -> Created.INSTANCE;
            case "CONFIRMED" -> Confirmed.INSTANCE;
            case "CLOSED" -> Closed.INSTANCE;
            case "CANCELLED" -> Cancelled.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown order state: " + value);
        };
    }
}