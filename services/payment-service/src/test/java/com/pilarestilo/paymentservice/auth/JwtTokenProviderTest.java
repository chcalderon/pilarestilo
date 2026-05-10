package com.pilarestilo.paymentservice.auth;

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
    void parses_and_validates_token() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        JwtTokenProvider provider = new JwtTokenProvider(Encoders.BASE64.encode(key.getEncoded()));
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
    }

    @Test
    void invalid_token_returns_false() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        JwtTokenProvider provider = new JwtTokenProvider(Encoders.BASE64.encode(key.getEncoded()));

        assertFalse(provider.isValid("invalid"));
    }
}
