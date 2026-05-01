package com.pilarestilo.productai.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductAiOllamaStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductAiOllamaStartupValidator.class);

    private final ProductAiOllamaClient ollamaClient;
    private final boolean validateOnStartup;
    private final boolean failFast;
    private final String productAiEngine;

    public ProductAiOllamaStartupValidator(
            ProductAiOllamaClient ollamaClient,
            @Value("${app.product-ai.ollama.validate-on-startup:true}") boolean validateOnStartup,
            @Value("${app.product-ai.ollama.fail-fast:false}") boolean failFast,
            @Value("${app.product-ai.engine:stub}") String productAiEngine
    ) {
        this.ollamaClient = ollamaClient;
        this.validateOnStartup = validateOnStartup;
        this.failFast = failFast;
        this.productAiEngine = productAiEngine;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!validateOnStartup || !"node_bridge".equalsIgnoreCase(productAiEngine)) {
            return;
        }

        ProductAiOllamaClient.ReadinessStatus readiness = ollamaClient.checkReadiness();
        if (readiness.ready()) {
            log.info(
                    "product_ai_ollama_startup_ready=true reason={} model={} base_url={}",
                    readiness.reason(),
                    readiness.model(),
                    readiness.baseUrl()
            );
            return;
        }

        String message = "Ollama no esta listo al inicio (reason=%s, model=%s, base_url=%s). "
                .formatted(readiness.reason(), readiness.model(), readiness.baseUrl());
        if (failFast) {
            throw new IllegalStateException(message + "Configura modelo o servicio antes de iniciar backend.");
        }

        log.warn(
                "{} El worker IA quedara pausado hasta que Ollama responda y tenga el modelo cargado.",
                message
        );
    }
}

