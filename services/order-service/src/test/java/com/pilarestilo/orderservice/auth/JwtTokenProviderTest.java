package com.pilarestilo.orderservice.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    @Test
    void parse_token_and_validate_successfully() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String secretBase64 = Encoders.BASE64.encode(key.getEncoded());
        JwtTokenProvider provider = new JwtTokenProvider(secretBase64);

        String userId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(userId)
                .claim("email", "admin@pilarestilo.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertTrue(provider.isValid(token));
        assertEquals(userId, provider.parseToken(token).getSubject());
        assertEquals("admin@pilarestilo.com", provider.parseToken(token).get("email", String.class));
        assertEquals("ADMIN", provider.parseToken(token).get("role", String.class));
    }

    @Test
    void is_valid_returns_false_for_invalid_token() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String secretBase64 = Encoders.BASE64.encode(key.getEncoded());
        JwtTokenProvider provider = new JwtTokenProvider(secretBase64);

        assertFalse(provider.isValid("not-a-jwt"));
    }
}
