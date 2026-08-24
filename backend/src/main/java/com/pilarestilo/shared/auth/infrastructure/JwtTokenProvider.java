package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.user.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// All time computation already uses java.time.Instant; java.util.Date only appears at the JJWT
// builder boundary, since issuedAt()/expiration() take no Instant overload. (Sonar's S2143 on
// this file is a file-level finding with no line to attach @SuppressWarnings to -- resolved
// won't-fix in SonarQube instead, with the same justification.)
@Component
public class JwtTokenProvider {

    private static final long ACCESS_EXPIRY_MS  = 24L * 60 * 60 * 1000;      // 24 h
    private static final long REFRESH_EXPIRY_MS = 7L  * 24 * 60 * 60 * 1000; // 7 d

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateAccessToken(UUID userId, String email, UserRole role, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ACCESS_EXPIRY_MS)))
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(UUID userId,
                                      String email,
                                      UserRole role,
                                      List<String> permissions,
                                      List<String> permissionCodes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .claim("permissions", permissions)
                .claim("permissionCodes", permissionCodes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ACCESS_EXPIRY_MS)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(REFRESH_EXPIRY_MS)))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException _) {
            return false;
        }
    }
}
