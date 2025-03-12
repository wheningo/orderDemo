package com.example.orderdemo.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * @author whening
 * @description
 * @date 2025/3/12 18:38
 * @since 1.0
 **/
@Service
public class InventoryService {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @KafkaListener(topics = "order-events", groupId = "inventory-group")
    public void processOrder(String message) {

        if (message.startsWith("ORDER_CREATED:")) {
            Long orderId = Long.parseLong(message.split(":")[1]);
            boolean success = deductInventory(orderId); // 模拟库存扣减
            if (!success) {
                kafkaTemplate.send("order-events", orderId.toString(), "INVENTORY_FAILED:" + orderId);
            }
        }
    }

    private boolean deductInventory(Long orderId) {
        // 模拟库存扣减失败（随机失败）
        return Math.random() > 0.2; // 80% 成功率
    }
}
 
