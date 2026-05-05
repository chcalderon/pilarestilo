package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.isValid(token)) {
            Claims claims = jwtTokenProvider.parseToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            var user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isActive()) {
                filterChain.doFilter(request, response);
                return;
            }
            String email = claims.get("email", String.class);
            UserRole role = UserRole.valueOf(claims.get("role", String.class));
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            if (permissions == null) permissions = List.of();

            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, email, role, permissions), null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
