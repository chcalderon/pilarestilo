package com.pilarestilo.payment.infrastructure.adapters;

import tools.jackson.databind.JsonNode;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.ports.PaymentGatewayPort;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.payment.gateway.provider", havingValue = "MERCADO_PAGO")
public class MercadoPagoPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentGatewayAdapter.class);

    public record WebhookResolution(
            UUID orderId,
            String gatewayStatus,
            String gatewayReference
    ) {}

    private static final Duration CHECKOUT_TTL_FALLBACK = Duration.ofMinutes(30);

    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envApiBaseUrl;
    private final String envAccessToken;
    private final String envSuccessUrl;
    private final String envPendingUrl;
    private final String envFailureUrl;
    private final String envNotificationUrl;
    private final String envWebhookToken;

    public MercadoPagoPaymentGatewayAdapter(
            RestClient.Builder restClientBuilder,
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.payment.gateway.mercadopago.api-base-url:https://api.mercadopago.com}") String apiBaseUrl,
            @Value("${app.payment.gateway.mercadopago.access-token:}") String accessToken,
            @Value("${app.payment.gateway.mercadopago.success-url:}") String successUrl,
            @Value("${app.payment.gateway.mercadopago.pending-url:}") String pendingUrl,
            @Value("${app.payment.gateway.mercadopago.failure-url:}") String failureUrl,
            @Value("${app.payment.gateway.mercadopago.notification-url:}") String notificationUrl,
            @Value("${app.payment.gateway.mercadopago.webhook-token:}") String webhookToken
    ) {
        this.restClientBuilder = restClientBuilder;
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envApiBaseUrl = normalize(apiBaseUrl);
        this.envAccessToken = normalize(accessToken);
        this.envSuccessUrl = normalize(successUrl);
        this.envPendingUrl = normalize(pendingUrl);
        this.envFailureUrl = normalize(failureUrl);
        this.envNotificationUrl = normalize(notificationUrl);
        this.envWebhookToken = normalize(webhookToken);
    }

    @Override
    public CheckoutSession initiatePayment(UUID orderId, Money amount) {
        EffectiveConfig config = resolveEffectiveConfig(true);
        RestClient restClient = buildRestClient(config);
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPreferencePayload(orderId, amount, config))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception _) {
            throw new DomainException("Could not create Mercado Pago checkout session");
        }

        if (response == null) {
            throw new DomainException("Mercado Pago checkout session response was empty");
        }

        String gatewayReference = text(response, "id");
        String checkoutUrl = firstNonBlank(text(response, "init_point"), text(response, "sandbox_init_point"));

        if (gatewayReference.isBlank() || checkoutUrl.isBlank()) {
            throw new DomainException("Mercado Pago response missing checkout data");
        }

        Instant expiresAt = parseInstant(firstNonBlank(text(response, "date_of_expiration"), text(response, "expiration_date_to")));
        if (expiresAt == null) {
            expiresAt = Instant.now().plus(CHECKOUT_TTL_FALLBACK);
        }

        return new CheckoutSession(gatewayReference, checkoutUrl, expiresAt);
    }

    @Override
    public PaymentStatus checkStatus(String gatewayReference) {
        if (gatewayReference == null || gatewayReference.isBlank()) {
            return PaymentStatus.PENDING;
        }
        EffectiveConfig config = resolveEffectiveConfig(true);
        RestClient restClient = buildRestClient(config);

        try {
            JsonNode response = restClient.get()
                    .uri("/checkout/preferences/{id}", gatewayReference.trim())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return PaymentStatus.PENDING;
            }
            String status = text(response, "status");
            return switch (status.toLowerCase(Locale.ROOT)) {
                case "expired", "cancelled" -> PaymentStatus.REJECTED;
                default -> PaymentStatus.PENDING;
            };
        } catch (Exception _) {
            return PaymentStatus.PENDING;
        }
    }

    public WebhookResolution resolvePaymentWebhook(String mercadoPagoPaymentId) {
        if (mercadoPagoPaymentId == null || mercadoPagoPaymentId.isBlank()) {
            throw new DomainException("Mercado Pago payment id is required");
        }
        EffectiveConfig config = resolveEffectiveConfig(true);
        RestClient restClient = buildRestClient(config);

        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/v1/payments/{id}", mercadoPagoPaymentId.trim())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception _) {
            throw new DomainException("Could not resolve Mercado Pago payment webhook");
        }

        if (response == null) {
            throw new DomainException("Mercado Pago payment lookup returned empty response");
        }

        String externalReference = text(response, "external_reference");
        if (externalReference.isBlank()) {
            throw new DomainException("Mercado Pago payment does not include external_reference");
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(externalReference.trim());
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid external_reference received from Mercado Pago");
        }

        String gatewayStatus = text(response, "status");
        if (gatewayStatus.isBlank()) {
            throw new DomainException("Mercado Pago payment status is missing");
        }

        String gatewayReference = firstNonBlank(text(response, "id"), mercadoPagoPaymentId);
        return new WebhookResolution(orderId, gatewayStatus, gatewayReference);
    }

    public boolean isWebhookTokenValid(String providedToken) {
        String expectedToken = resolveEffectiveConfig(false).webhookToken();
        if (expectedToken.isBlank()) {
            return true;
        }
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> buildPreferencePayload(UUID orderId, Money amount, EffectiveConfig config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_reference", orderId.toString());
        payload.put("metadata", Map.of("order_id", orderId.toString(), "platform", "pilar-estilo"));
        payload.put("items", List.of(buildItem(orderId, amount)));

        Map<String, String> backUrls = buildBackUrls(config);
        if (!backUrls.isEmpty()) {
            payload.put("back_urls", backUrls);
            if (backUrls.containsKey("success")) {
                payload.put("auto_return", "approved");
            }
        }

        String builtNotification = buildNotificationUrl(orderId, config);
        if (!builtNotification.isBlank()) {
            payload.put("notification_url", builtNotification);
        }
        return payload;
    }

    private Map<String, Object> buildItem(UUID orderId, Money amount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", orderId.toString());
        item.put("title", "Pedido Pilar Estilo " + orderId.toString().substring(0, 8));
        item.put("quantity", 1);
        item.put("currency_id", amount.currency());
        item.put("unit_price", amount.amount().setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private Map<String, String> buildBackUrls(EffectiveConfig config) {
        Map<String, String> backUrls = new LinkedHashMap<>();
        if (!config.successUrl().isBlank()) {
            backUrls.put("success", config.successUrl());
        }
        if (!config.pendingUrl().isBlank()) {
            backUrls.put("pending", config.pendingUrl());
        }
        if (!config.failureUrl().isBlank()) {
            backUrls.put("failure", config.failureUrl());
        }
        return backUrls;
    }

    private String buildNotificationUrl(UUID orderId, EffectiveConfig config) {
        if (config.notificationUrl().isBlank()) {
            return "";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(config.notificationUrl())
                .queryParam("orderId", orderId.toString());
        if (!config.webhookToken().isBlank()) {
            builder.queryParam("token", config.webhookToken());
        }
        return builder.build(true).toUriString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asString("");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private EffectiveConfig resolveEffectiveConfig(boolean requireAccessToken) {
        var settings = systemSettingsRepository.get();

        String apiBaseUrl = firstNonBlank(settings.getPaymentGatewayMpApiBaseUrl(), envApiBaseUrl);
        String accessToken = firstNonBlank(
                decryptSecret(settings.getPaymentGatewayMpAccessTokenEncrypted(), "Mercado Pago access token"),
                envAccessToken
        );
        String successUrl = firstNonBlank(settings.getPaymentGatewayMpSuccessUrl(), envSuccessUrl);
        String pendingUrl = firstNonBlank(settings.getPaymentGatewayMpPendingUrl(), envPendingUrl);
        String failureUrl = firstNonBlank(settings.getPaymentGatewayMpFailureUrl(), envFailureUrl);
        String notificationUrl = firstNonBlank(settings.getPaymentGatewayMpNotificationUrl(), envNotificationUrl);
        String webhookToken = firstNonBlank(
                decryptSecret(settings.getPaymentGatewayMpWebhookTokenEncrypted(), "Mercado Pago webhook token"),
                envWebhookToken
        );

        if (requireAccessToken && accessToken.isBlank()) {
            throw new DomainException(
                    "Mercado Pago access token is missing. Configure it in admin settings or PAYMENT_GATEWAY_MP_ACCESS_TOKEN."
            );
        }

        return new EffectiveConfig(
                normalize(apiBaseUrl).isBlank() ? "https://api.mercadopago.com" : normalize(apiBaseUrl),
                normalize(accessToken),
                normalize(successUrl),
                normalize(pendingUrl),
                normalize(failureUrl),
                normalize(notificationUrl),
                normalize(webhookToken)
        );
    }

    private RestClient buildRestClient(EffectiveConfig config) {
        return restClientBuilder
                .baseUrl(config.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String decryptSecret(String encryptedValue, String label) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encryptedValue);
            if (decrypted == null || decrypted.isBlank()) {
                return null;
            }
            return decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[PAYMENT:GATEWAY:MP] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    private record EffectiveConfig(
            String apiBaseUrl,
            String accessToken,
            String successUrl,
            String pendingUrl,
            String failureUrl,
            String notificationUrl,
            String webhookToken
    ) {}
}
