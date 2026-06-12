package com.example.riskcontrol.engine;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class DroolsConfig {

    private static final Logger log = LoggerFactory.getLogger(DroolsConfig.class);

    @Value("${nacos.config.server-addr:localhost:8848}")
    private String nacosAddr;

    @Value("${nacos.config.data-id:risk-rules.drl}")
    private String dataId;

    @Value("${nacos.config.group:RISK_CONTROL}")
    private String group;

    private final AtomicReference<KieContainer> containerRef = new AtomicReference<>();

    @Bean
    public AtomicReference<KieContainer> kieContainerRef() {
        return containerRef;
    }

    @PostConstruct
    public void init() {
        // First: load from classpath as fallback
        String rules = loadFromClasspath();
        containerRef.set(buildContainer(rules));

        // Then: try Nacos, listen for hot-reload
        try {
            Properties props = new Properties();
            props.put("serverAddr", nacosAddr);
            ConfigService configService = NacosFactory.createConfigService(props);

            String nacosRules = configService.getConfig(dataId, group, 3000);
            if (nacosRules != null && !nacosRules.isBlank()) {
                containerRef.set(buildContainer(nacosRules));
                log.info("Drools rules loaded from Nacos: dataId={}, group={}", dataId, group);
            } else {
                log.info("No rules in Nacos (dataId={}, group={}), using classpath fallback", dataId, group);
            }

            // Listen for changes — hot-reload
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() { return null; }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Nacos rule update received, rebuilding KieContainer...");
                    try {
                        KieContainer newContainer = buildContainer(configInfo);
                        containerRef.set(newContainer);
                        log.info("Drools rules hot-reloaded successfully");
                    } catch (Exception e) {
                        log.error("Failed to hot-reload rules, keeping previous version: {}", e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Nacos unavailable, using classpath rules (hot-reload disabled): {}", e.getMessage());
        }
    }

    private KieContainer buildContainer(String drlContent) {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        kieFileSystem.write("src/main/resources/rules/risk-rules.drl",
                kieServices.getResources().newByteArrayResource(drlContent.getBytes()));

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Drools compilation errors: " + results.getMessages());
        }

        KieModule kieModule = kieBuilder.getKieModule();
        return kieServices.newKieContainer(kieModule.getReleaseId());
    }

    private String loadFromClasspath() {
        try (var is = getClass().getResourceAsStream("/rules/risk-rules.drl")) {
            if (is != null) {
                return new String(is.readAllBytes());
            }
        } catch (Exception e) {
            log.warn("Failed to read classpath rules: {}", e.getMessage());
        }
        return "";
    }
}