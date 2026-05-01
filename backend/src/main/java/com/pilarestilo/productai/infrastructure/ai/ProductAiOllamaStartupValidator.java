package com.pilarestilo.productai.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProductAiOllamaStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductAiOllamaStartupValidator.class);

    private final ProductAiOllamaClient ollamaClient;
    private final boolean validateOnStartup;
    private final boolean failFast;
    private final boolean warmupOnStartup;
    private final boolean warmupBlockingOnStartup;
    private final long warmupTimeoutMs;
    private final String productAiEngine;

    public ProductAiOllamaStartupValidator(
            ProductAiOllamaClient ollamaClient,
            @Value("${app.product-ai.ollama.validate-on-startup:true}") boolean validateOnStartup,
            @Value("${app.product-ai.ollama.fail-fast:false}") boolean failFast,
            @Value("${app.product-ai.ollama.warmup-on-startup:true}") boolean warmupOnStartup,
            @Value("${app.product-ai.ollama.warmup-blocking-on-startup:true}") boolean warmupBlockingOnStartup,
            @Value("${app.product-ai.ollama.warmup-timeout-ms:300000}") long warmupTimeoutMs,
            @Value("${app.product-ai.engine:stub}") String productAiEngine
    ) {
        this.ollamaClient = ollamaClient;
        this.validateOnStartup = validateOnStartup;
        this.failFast = failFast;
        this.warmupOnStartup = warmupOnStartup;
        this.warmupBlockingOnStartup = warmupBlockingOnStartup;
        this.warmupTimeoutMs = warmupTimeoutMs;
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
            triggerWarmupIfEnabled(readiness);
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

    private void triggerWarmupIfEnabled(ProductAiOllamaClient.ReadinessStatus readiness) {
        if (!warmupOnStartup) {
            return;
        }

        if (warmupBlockingOnStartup) {
            long startedAt = System.currentTimeMillis();
            CompletableFuture<Void> warmupFuture = CompletableFuture.runAsync(ollamaClient::warmUp);
            try {
                warmupFuture.get(warmupTimeoutMs, TimeUnit.MILLISECONDS);
                long elapsed = System.currentTimeMillis() - startedAt;
                log.info(
                        "product_ai_ollama_warmup_done mode=blocking model={} base_url={} elapsed_ms={} timeout_ms={}",
                        readiness.model(),
                        readiness.baseUrl(),
                        elapsed,
                        warmupTimeoutMs
                );
                return;
            } catch (TimeoutException ex) {
                warmupFuture.cancel(true);
                handleWarmupFailure(readiness, "timeout", ex);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handleWarmupFailure(readiness, "interrupted", ex);
                return;
            } catch (ExecutionException ex) {
                handleWarmupFailure(readiness, "execution-error", ex);
                return;
            }
        }

        CompletableFuture.runAsync(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                ollamaClient.warmUp();
                long elapsed = System.currentTimeMillis() - startedAt;
                log.info(
                        "product_ai_ollama_warmup_done mode=async model={} base_url={} elapsed_ms={}",
                        readiness.model(),
                        readiness.baseUrl(),
                        elapsed
                );
            } catch (Exception ex) {
                handleWarmupFailure(readiness, "async-error", ex);
            }
        });
    }

    private void handleWarmupFailure(ProductAiOllamaClient.ReadinessStatus readiness, String reason, Exception ex) {
        String message = "Warmup Ollama fallo en startup (reason=%s, model=%s, base_url=%s)."
                .formatted(reason, readiness.model(), readiness.baseUrl());
        if (failFast) {
            throw new IllegalStateException(message + " fail-fast=true, abortando inicio de backend.", ex);
        }

        log.warn("{} El primer infer puede tardar mas por cold start.", message);
    }
}
