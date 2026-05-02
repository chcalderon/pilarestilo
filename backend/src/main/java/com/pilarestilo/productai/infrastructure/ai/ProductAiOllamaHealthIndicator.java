package com.pilarestilo.productai.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("productAiOllama")
public class ProductAiOllamaHealthIndicator implements HealthIndicator {

    private final ProductAiOllamaClient ollamaClient;
    private final String productAiEngine;

    public ProductAiOllamaHealthIndicator(
            ProductAiOllamaClient ollamaClient,
            @Value("${app.product-ai.engine:stub}") String productAiEngine
    ) {
        this.ollamaClient = ollamaClient;
        this.productAiEngine = productAiEngine;
    }

    @Override
    public Health health() {
        if (!usesOllamaEngine()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("reason", "engine-not-ollama")
                    .build();
        }

        ProductAiOllamaClient.ReadinessStatus readiness = ollamaClient.checkReadiness();
        if (readiness.ready()) {
            return Health.up()
                    .withDetail("ready", true)
                    .withDetail("reachable", readiness.reachable())
                    .withDetail("modelAvailable", readiness.modelAvailable())
                    .withDetail("model", readiness.model())
                    .withDetail("baseUrl", readiness.baseUrl())
                    .build();
        }

        return Health.status("DEGRADED")
                .withDetail("ready", false)
                .withDetail("reachable", readiness.reachable())
                .withDetail("modelAvailable", readiness.modelAvailable())
                .withDetail("reason", readiness.reason())
                .withDetail("model", readiness.model())
                .withDetail("baseUrl", readiness.baseUrl())
                .build();
    }

    private boolean usesOllamaEngine() {
        return "node_bridge".equalsIgnoreCase(productAiEngine)
                || "ollama_backend".equalsIgnoreCase(productAiEngine);
    }
}
