package com.pilarestilo.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiGatewayRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_KEYS_BEFORE_CLEANUP = 20_000;

    private final boolean enabled;
    private final int windowSeconds;
    private final int loginMaxRequests;
    private final int registerMaxRequests;
    private final int forgotPasswordMaxRequests;
    private final int webhookMaxRequests;

    private final Map<String, CounterWindow> counters = new ConcurrentHashMap<>();

    public ApiGatewayRateLimitFilter(
            @Value("${app.gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.gateway.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${app.gateway.rate-limit.login-max-requests:12}") int loginMaxRequests,
            @Value("${app.gateway.rate-limit.register-max-requests:6}") int registerMaxRequests,
            @Value("${app.gateway.rate-limit.forgot-password-max-requests:5}") int forgotPasswordMaxRequests,
            @Value("${app.gateway.rate-limit.webhook-max-requests:180}") int webhookMaxRequests
    ) {
        this.enabled = enabled;
        this.windowSeconds = Math.max(1, windowSeconds);
        this.loginMaxRequests = Math.max(1, loginMaxRequests);
        this.registerMaxRequests = Math.max(1, registerMaxRequests);
        this.forgotPasswordMaxRequests = Math.max(1, forgotPasswordMaxRequests);
        this.webhookMaxRequests = Math.max(1, webhookMaxRequests);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitPolicy policy = resolvePolicy(request.getMethod(), request.getRequestURI());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        long nowEpochSecond = Instant.now().getEpochSecond();
        String key = policy.keyPrefix + ":" + clientIp;

        CounterWindow counter = counters.computeIfAbsent(key, k -> new CounterWindow(nowEpochSecond));
        int currentCount;
        synchronized (counter) {
            if (nowEpochSecond - counter.windowStartEpochSecond >= windowSeconds) {
                counter.windowStartEpochSecond = nowEpochSecond;
                counter.count.set(0);
            }
            currentCount = counter.count.incrementAndGet();
        }

        if (currentCount > policy.maxRequests) {
            int retryAfterSeconds = windowSeconds;
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("{\"message\":\"Too many requests. Please retry later.\"}");
            return;
        }

        cleanupIfNeeded(nowEpochSecond);
        filterChain.doFilter(request, response);
    }

    private RateLimitPolicy resolvePolicy(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        if ("/api/auth/login".equals(path)) {
            return new RateLimitPolicy("login", loginMaxRequests);
        }
        if ("/api/auth/register".equals(path)) {
            return new RateLimitPolicy("register", registerMaxRequests);
        }
        if ("/api/auth/forgot-password".equals(path)) {
            return new RateLimitPolicy("forgot_password", forgotPasswordMaxRequests);
        }
        if ("/api/payments/webhooks/gateway".equals(path)
                || "/api/payments/webhooks/gateway/mercadopago".equals(path)) {
            return new RateLimitPolicy("payment_webhook", webhookMaxRequests);
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            if (commaIndex > 0) {
                return forwardedFor.substring(0, commaIndex).trim();
            }
            return forwardedFor.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupIfNeeded(long nowEpochSecond) {
        if (counters.size() < MAX_TRACKED_KEYS_BEFORE_CLEANUP) {
            return;
        }
        long threshold = nowEpochSecond - (windowSeconds * 2L);
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartEpochSecond < threshold);
    }

    private record RateLimitPolicy(
            String keyPrefix,
            int maxRequests
    ) {
    }

    private static final class CounterWindow {
        private volatile long windowStartEpochSecond;
        private final AtomicInteger count = new AtomicInteger();

        private CounterWindow(long windowStartEpochSecond) {
            this.windowStartEpochSecond = windowStartEpochSecond;
        }
    }
}
