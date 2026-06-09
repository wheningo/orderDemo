package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.domain.hotrank.InteractionEvent;

public interface InteractionEventHandler {
    void handle(InteractionEvent event);
}