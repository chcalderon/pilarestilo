package com.pilarestilo.publication.infrastructure.n8n;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.publication.application.dto.PublicationDispatchWebhookPayload;
import com.pilarestilo.publication.application.ports.PublicationWebhookDispatcher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class N8nPublicationWebhookDispatcher implements PublicationWebhookDispatcher {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final SocialPublishingN8nConfigResolver configResolver;

    public N8nPublicationWebhookDispatcher(RestClient.Builder restClientBuilder,
                                           ObjectMapper objectMapper,
                                           SocialPublishingN8nConfigResolver configResolver) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.configResolver = configResolver;
    }

    @Override
    public DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchWebhookPayload payload) {
        SocialPublishingN8nConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (!config.hasWebhookUrl()) {
            return new DispatchResult("disabled-no-webhook", sha256Hex(toJson(payload)));
        }

        String json = toJson(payload);
        String signature = sign(json, config.apiKey());
        String requestId = UUID.randomUUID().toString();

        try {
            RestClient.RequestBodySpec request = restClientBuilder.build()
                    .post()
                    .uri(config.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-PE-SIGNATURE", signature)
                    .header("X-PE-EVENT-TYPE", payload.eventType())
                    .header("X-PE-IDEMPOTENCY-KEY", idempotencyKey)
                    .header("X-PE-PUBLICATION-ID", publicationId.toString())
                    .header("X-PE-EVENT-ID", requestId);
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                request = request.header(config.tokenHeaderName(), config.apiKey());
            }
            request.body(payload).retrieve().toBodilessEntity();
            return new DispatchResult(requestId, sha256Hex(json));
        } catch (Exception ex) {
            throw new DomainException("Could not dispatch publication webhook: " + ex.getMessage());
        }
    }

    public boolean isValidCallbackToken(String providedToken) {
        SocialPublishingN8nConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (!config.hasCallbackToken()) {
            return false;
        }
        return MessageDigest.isEqual(
                config.callbackToken().getBytes(StandardCharsets.UTF_8),
                (providedToken == null ? "" : providedToken.trim()).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            throw new DomainException("Could not serialize publication webhook payload");
        }
    }

    private String sign(String body, String secret) {
        if (secret == null || secret.isBlank()) {
            return sha256Hex(body);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new DomainException("Could not sign publication webhook payload");
        }
    }

    private String sha256Hex(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new DomainException("Could not hash publication webhook payload");
        }
    }
}
