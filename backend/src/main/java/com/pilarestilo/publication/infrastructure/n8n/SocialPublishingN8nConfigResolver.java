package com.pilarestilo.publication.infrastructure.n8n;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SocialPublishingN8nConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(SocialPublishingN8nConfigResolver.class);
    private static final String DEFAULT_TOKEN_HEADER_NAME = "X-PE-N8N-TOKEN";

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envWebhookUrl;
    private final String envApiKey;
    private final String envTokenHeaderName;
    private final String envCallbackToken;

    public SocialPublishingN8nConfigResolver(SystemSettingsRepository systemSettingsRepository,
                                             SystemSettingsCryptoService cryptoService,
                                             @Value("${app.social-publishing.n8n.webhook-url:}") String webhookUrl,
                                             @Value("${app.social-publishing.n8n.api-key:}") String apiKey,
                                             @Value("${app.social-publishing.n8n.token-header-name:X-PE-N8N-TOKEN}") String tokenHeaderName,
                                             @Value("${app.social-publishing.n8n.callback-token:}") String callbackToken) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envWebhookUrl = normalize(webhookUrl);
        this.envApiKey = normalize(apiKey);
        this.envTokenHeaderName = normalize(tokenHeaderName);
        this.envCallbackToken = normalize(callbackToken);
    }

    public EffectiveConfig resolve() {
        var settings = systemSettingsRepository.get();
        String webhookUrl = firstNonBlank(envWebhookUrl, settings.getN8nWebhookUrl());
        String decryptedApiKey = decryptSecret(settings.getN8nApiKeyEncrypted(), "n8n api key");
        String apiKey = firstNonBlank(envApiKey, decryptedApiKey);
        String tokenHeaderName = normalizeHeaderName(firstNonBlank(envTokenHeaderName, settings.getN8nTokenHeaderName()));
        String callbackToken = firstNonBlank(envCallbackToken, apiKey);

        return new EffectiveConfig(webhookUrl, apiKey, tokenHeaderName, callbackToken);
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
            log.warn("[SOCIAL:N8N] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private String normalizeHeaderName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TOKEN_HEADER_NAME;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record EffectiveConfig(
            String webhookUrl,
            String apiKey,
            String tokenHeaderName,
            String callbackToken
    ) {
        public boolean hasWebhookUrl() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }

        public boolean hasCallbackToken() {
            return callbackToken != null && !callbackToken.isBlank();
        }
    }
}
