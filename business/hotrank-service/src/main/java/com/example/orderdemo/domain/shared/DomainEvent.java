package com.example.orderdemo.domain.shared;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}