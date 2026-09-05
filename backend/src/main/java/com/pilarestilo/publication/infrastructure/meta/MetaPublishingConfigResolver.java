package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Instagram/Facebook posting credentials the same way SocialPublishingN8nConfigResolver
 * resolved its webhook config: system_settings (encrypted) first, env var fallback second. Base
 * URLs are env-only — they are endpoint choices, not secrets, and don't need panel editing.
 */
@Component
public class MetaPublishingConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(MetaPublishingConfigResolver.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envInstagramUserId;
    private final String envInstagramAccessToken;
    private final String instagramBaseUrl;
    private final String envFacebookPageId;
    private final String envFacebookPageAccessToken;
    private final String facebookBaseUrl;
    private final String publicMediaBaseUrl;

    public MetaPublishingConfigResolver(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.social-publishing.meta.instagram.user-id:}") String envInstagramUserId,
            @Value("${app.social-publishing.meta.instagram.access-token:}") String envInstagramAccessToken,
            @Value("${app.social-publishing.meta.instagram.base-url:https://graph.instagram.com/v23.0}") String instagramBaseUrl,
            @Value("${app.social-publishing.meta.facebook.page-id:}") String envFacebookPageId,
            @Value("${app.social-publishing.meta.facebook.page-access-token:}") String envFacebookPageAccessToken,
            @Value("${app.social-publishing.meta.facebook.base-url:https://graph.facebook.com/v23.0}") String facebookBaseUrl,
            @Value("${app.social-publishing.meta.public-media-base-url:}") String publicMediaBaseUrl
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envInstagramUserId = normalize(envInstagramUserId);
        this.envInstagramAccessToken = normalize(envInstagramAccessToken);
        this.instagramBaseUrl = instagramBaseUrl;
        this.envFacebookPageId = normalize(envFacebookPageId);
        this.envFacebookPageAccessToken = normalize(envFacebookPageAccessToken);
        this.facebookBaseUrl = facebookBaseUrl;
        this.publicMediaBaseUrl = normalize(publicMediaBaseUrl);
    }

    public EffectiveConfig resolve() {
        var settings = systemSettingsRepository.get();
        String instagramUserId = firstNonBlank(envInstagramUserId, settings.getMetaInstagramUserId());
        String instagramAccessToken = firstNonBlank(envInstagramAccessToken,
                decryptSecret(settings.getMetaInstagramAccessTokenEncrypted(), "meta instagram access token"));
        String facebookPageId = firstNonBlank(envFacebookPageId, settings.getMetaFacebookPageId());
        String facebookPageAccessToken = firstNonBlank(envFacebookPageAccessToken,
                decryptSecret(settings.getMetaFacebookPageAccessTokenEncrypted(), "meta facebook page access token"));

        return new EffectiveConfig(
                instagramUserId, instagramAccessToken, instagramBaseUrl,
                facebookPageId, facebookPageAccessToken, facebookBaseUrl,
                publicMediaBaseUrl.isBlank() ? null : publicMediaBaseUrl
        );
    }

    private String decryptSecret(String encryptedValue, String label) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encryptedValue);
            return (decrypted == null || decrypted.isBlank()) ? null : decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[SOCIAL:META] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
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
            String instagramUserId,
            String instagramAccessToken,
            String instagramBaseUrl,
            String facebookPageId,
            String facebookPageAccessToken,
            String facebookBaseUrl,
            String publicMediaBaseUrl
    ) {}
}
