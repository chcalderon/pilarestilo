package com.pilarestilo.notificationservice.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A permitAll liveness ping under the same path prefix Caddy routes here, so the gateway can health
 * a bare {@code /api/notifications/_health} without a token — the same pattern order-service uses
 * for {@code /api/orders/_health}.
 */
@RestController
public class HealthController {

    @GetMapping("/api/notifications/_health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "notification-service");
    }
}
