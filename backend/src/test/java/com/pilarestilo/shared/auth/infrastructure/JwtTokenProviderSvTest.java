package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderSvTest {

    private static final String SECRET = "U2VjcmV0U2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET);

    @Test
    void access_token_carries_the_session_version() {
        String token = provider.generateAccessToken(
                UUID.randomUUID(), "a@b.com", UserRole.CUSTOMER, List.of(), List.of(), 4);
        assertThat(provider.parseToken(token).get("sv", Integer.class)).isEqualTo(4);
    }

    @Test
    void refresh_token_carries_the_session_version() {
        String token = provider.generateRefreshToken(UUID.randomUUID(), 4);
        assertThat(provider.parseToken(token).get("sv", Integer.class)).isEqualTo(4);
    }

    @Test
    void the_legacy_five_arg_access_token_defaults_the_session_version_to_1() {
        String token = provider.generateAccessToken(
                UUID.randomUUID(), "a@b.com", UserRole.CUSTOMER, List.of(), List.of());
        assertThat(provider.parseToken(token).get("sv", Integer.class)).isEqualTo(1);
    }

    @Test
    void the_legacy_one_arg_refresh_token_defaults_the_session_version_to_1() {
        String token = provider.generateRefreshToken(UUID.randomUUID());
        assertThat(provider.parseToken(token).get("sv", Integer.class)).isEqualTo(1);
    }
}
