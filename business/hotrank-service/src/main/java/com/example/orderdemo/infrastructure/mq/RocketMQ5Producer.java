package com.example.orderdemo.infrastructure.mq;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMQ5Producer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQ5Producer.class);
    private static final String TOPIC = "reservation-timeout-guard";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String endpoint;

    private ClientServiceProvider provider;
    private Producer producer;

    @PostConstruct
    public void init() throws Exception {
        provider = ClientServiceProvider.loadService();
        ClientConfiguration config = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint)
                .setRequestTimeout(Duration.ofSeconds(5))
                .build();
        producer = provider.newProducerBuilder()
                .setClientConfiguration(config)
                .setTopics(TOPIC)
                .build();
        log.info("RocketMQ 5.x producer initialized, endpoint={}", endpoint);
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (producer != null) {
            producer.close();
        }
    }

    public void sendScheduled(String key, String payload, long deliverTimeMillis) {
        try {
            Message message = provider.newMessageBuilder()
                    .setTopic(TOPIC)
                    .setKeys(key)
                    .setBody(payload.getBytes())
                    .setDeliveryTimestamp(deliverTimeMillis)
                    .build();
            SendReceipt receipt = producer.send(message);
            log.info("Scheduled message sent: key={}, deliverAt={}, msgId={}",
                    key, Instant.ofEpochMilli(deliverTimeMillis), receipt.getMessageId());
        } catch (Exception e) {
            log.error("Failed to send scheduled message: key={}", key, e);
            throw new RuntimeException("Failed to send RocketMQ scheduled message", e);
        }
    }
}