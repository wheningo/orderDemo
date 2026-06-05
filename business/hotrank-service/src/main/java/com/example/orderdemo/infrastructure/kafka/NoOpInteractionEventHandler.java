package com.example.orderdemo.infrastructure.kafka;

import com.example.orderdemo.domain.hotrank.InteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpInteractionEventHandler implements InteractionEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NoOpInteractionEventHandler.class);

    @Override
    public void handle(InteractionEvent event) {
        log.info("Received interaction: contentId={}, type={}, weight={}",
                 event.contentId(), event.interactionType(), event.weight());
    }
}