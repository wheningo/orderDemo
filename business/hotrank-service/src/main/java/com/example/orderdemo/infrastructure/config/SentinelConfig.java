package com.example.orderdemo.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        // Rate limit: inventory reserve — max 100 QPS
        FlowRule inventoryRule = new FlowRule();
        inventoryRule.setResource("POST:/inventory/reserve");
        inventoryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        inventoryRule.setCount(100);
        inventoryRule.setLimitApp("default");
        rules.add(inventoryRule);

        // Rate limit: place order — max 50 QPS
        FlowRule orderRule = new FlowRule();
        orderRule.setResource("POST:/orders/place");
        orderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        orderRule.setCount(50);
        orderRule.setLimitApp("default");
        rules.add(orderRule);

        // Rate limit: hot rank boost — max 200 QPS
        FlowRule boostRule = new FlowRule();
        boostRule.setResource("POST:/hotrank/boost");
        boostRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        boostRule.setCount(200);
        boostRule.setLimitApp("default");
        rules.add(boostRule);

        FlowRuleManager.loadRules(rules);
    }
}