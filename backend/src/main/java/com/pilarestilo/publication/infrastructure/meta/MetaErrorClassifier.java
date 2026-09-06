package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/** Decides whether a failed Meta dispatch should be retried automatically or failed for a human. */
final class MetaErrorClassifier {

    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(4, 17, 32, 613);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetaErrorClassifier() {
    }

    static boolean isRetryable(Throwable ex) {
        if (ex instanceof DomainException) {
            return false;                                   // missing config / no media URL — retrying changes nothing
        }
        if (ex instanceof HttpServerErrorException) {
            return true;                                    // 5xx
        }
        if (ex instanceof ResourceAccessException) {
            return true;                                    // connect / read timeout
        }
        if (ex instanceof HttpClientErrorException http) {
            if (http.getStatusCode().value() == 429) {
                return true;
            }
            Integer metaCode = extractMetaErrorCode(http.getResponseBodyAsString());
            return metaCode != null && RATE_LIMIT_CODES.contains(metaCode);
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.contains("was not ready after")) {
            return true;                                    // container still processing
        }
        if (msg.contains("error before it could be published")
                || msg.contains("expired before it could be published")) {
            return false;                                   // media rejected by Meta
        }
        return true;                                        // unknown adapter/parse error — give it the retry budget
    }

    private static Integer extractMetaErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode code = node.path("error").path("code");
            return code.isNumber() ? code.asInt() : null;
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }
}
