package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaPublishingConfigResolverTest {

    @Mock
    SystemSettingsRepository systemSettingsRepository;

    @Mock
    SystemSettingsCryptoService cryptoService;

    @Mock
    SystemSettings settings;

    @BeforeEach
    void setUp() {
        when(systemSettingsRepository.get()).thenReturn(settings);
    }

    @Test
    void env_values_win_over_system_settings_when_both_present() {
        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "env-ig-user", "env-ig-token", "https://graph.instagram.com/v23.0",
                "env-fb-page", "env-fb-token", "https://graph.facebook.com/v23.0",
                "https://pilarestilo.com"
        );

        var config = resolver.resolve();

        assertEquals("env-ig-user", config.instagramUserId());
        assertEquals("env-ig-token", config.instagramAccessToken());
        assertEquals("env-fb-page", config.facebookPageId());
        assertEquals("env-fb-token", config.facebookPageAccessToken());
        assertEquals("https://pilarestilo.com", config.publicMediaBaseUrl());
    }

    @Test
    void falls_back_to_decrypted_system_settings_when_env_is_blank() {
        when(settings.getMetaInstagramUserId()).thenReturn("db-ig-user");
        when(settings.getMetaInstagramAccessTokenEncrypted()).thenReturn("cipher-ig");
        when(cryptoService.decrypt("cipher-ig")).thenReturn("db-ig-token");

        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "", "", "https://graph.instagram.com/v23.0",
                "", "", "https://graph.facebook.com/v23.0",
                ""
        );

        var config = resolver.resolve();

        assertEquals("db-ig-user", config.instagramUserId());
        assertEquals("db-ig-token", config.instagramAccessToken());
    }

    @Test
    void returns_null_for_unconfigured_credentials() {
        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "", "", "https://graph.instagram.com/v23.0",
                "", "", "https://graph.facebook.com/v23.0",
                ""
        );

        var config = resolver.resolve();

        assertNull(config.instagramUserId());
        assertNull(config.instagramAccessToken());
        assertNull(config.publicMediaBaseUrl());
    }
}
