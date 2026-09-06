package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaErrorClassifierTest {

    @Test
    void http_429_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null)));
    }

    @Test
    void meta_code_190_bad_token_is_permanent() {
        var ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null,
                "{\"error\":{\"code\":190,\"message\":\"expired\"}}".getBytes(StandardCharsets.UTF_8), null);
        assertFalse(MetaErrorClassifier.isRetryable(ex));
    }

    @Test
    void meta_rate_limit_code_4_is_retryable() {
        var ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null,
                "{\"error\":{\"code\":4}}".getBytes(StandardCharsets.UTF_8), null);
        assertTrue(MetaErrorClassifier.isRetryable(ex));
    }

    @Test
    void http_5xx_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)));
    }

    @Test
    void connection_timeout_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(new ResourceAccessException("timeout", new IOException())));
    }

    @Test
    void container_not_ready_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                new IllegalStateException("Instagram media container was not ready after 10 checks")));
    }

    @Test
    void container_error_is_permanent() {
        assertFalse(MetaErrorClassifier.isRetryable(
                new IllegalStateException("Instagram media container error before it could be published")));
    }

    @Test
    void missing_config_domain_exception_is_permanent() {
        assertFalse(MetaErrorClassifier.isRetryable(
                new DomainException("Instagram credentials are not configured")));
    }

    @Test
    void unknown_runtime_error_defaults_to_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(new RuntimeException("something odd")));
    }
}
