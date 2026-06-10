package com.example.orderdemo.infrastructure.mq;

import com.example.orderdemo.application.inventory.InventoryTccService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class ReservationTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservationTimeoutConsumer.class);
    private static final String TOPIC = "reservation-timeout-guard";
    private static final String CONSUMER_GROUP = "reservation-timeout-consumer";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String endpoint;

    private final InventoryTccService inventoryTccService;
    private final ObjectMapper objectMapper;
    private PushConsumer consumer;

    public ReservationTimeoutConsumer(InventoryTccService inventoryTccService, ObjectMapper objectMapper) {
        this.inventoryTccService = inventoryTccService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        ClientConfiguration config = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint)
                .setRequestTimeout(Duration.ofSeconds(5))
                .build();
        consumer = provider.newPushConsumerBuilder()
                .setClientConfiguration(config)
                .setConsumerGroup(CONSUMER_GROUP)
                .setSubscriptionExpressions(Collections.singletonMap(TOPIC, FilterExpression.SUB_ALL))
                .setMessageListener(this::onMessage)
                .build();
        log.info("ReservationTimeoutConsumer started, endpoint={}", endpoint);
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
    }

    private ConsumeResult onMessage(MessageView message) {
        try {
            String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
            JsonNode payload = objectMapper.readTree(body);
            String txKey = payload.get("txKey").asText();
            String sku = payload.get("sku").asText();

            log.info("Timeout guard fired: txKey={}, sku={}", txKey, sku);
            inventoryTccService.cancel(txKey);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("Failed to process timeout guard message: {}", e.getMessage(), e);
            return ConsumeResult.FAILURE;
        }
    }
}