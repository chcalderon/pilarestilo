package com.pilarestilo.orderservice.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_HEADER = "X-Service-Token";
    private static final UUID INTERNAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JwtTokenProvider jwtTokenProvider;
    private final String internalToken;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   @Value("${app.order.internal-token:}") String internalToken) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (tryAuthenticateInternalToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(request);
        if (token != null && jwtTokenProvider.isValid(token)) {
            Claims claims = jwtTokenProvider.parseToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            UserRole role = UserRole.valueOf(claims.get("role", String.class));

            setAuthentication(request, new AuthenticatedUser(userId, email, role, false), role);
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryAuthenticateInternalToken(HttpServletRequest request) {
        if (internalToken.isBlank()) {
            return false;
        }
        String incomingToken = request.getHeader(INTERNAL_HEADER);
        if (incomingToken == null || incomingToken.isBlank()) {
            return false;
        }
        if (!internalToken.equals(incomingToken)) {
            return false;
        }

        setAuthentication(
                request,
                new AuthenticatedUser(INTERNAL_USER_ID, "internal@order-service", UserRole.ADMIN, true),
                UserRole.ADMIN
        );
        return true;
    }

    private void setAuthentication(HttpServletRequest request, AuthenticatedUser user, UserRole role) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
