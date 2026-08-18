package com.pilarestilo.shared.infrastructure.bootstrap;

import com.pilarestilo.shared.auth.infrastructure.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // When a controller throws, the container re-dispatches to /error, and that
                        // dispatch runs the filter chain again without the original authentication.
                        // Left denied, every internal error in the API answers 403, which reads as a
                        // permission problem and sends anyone debugging it in the wrong direction —
                        // it hid a ClassCastException that had the whole dashboard down.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/google").permitAll()
                        .requestMatchers("/api/auth/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/gateway").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/gateway/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publications/*/external-result").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/inventory/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/wishlist/shared/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/navigation/**").permitAll()
                        // Receipts are bank screenshots: the buyer's name, their account and the
                        // amount. They used to live under the public media root, readable by anyone
                        // holding the url. New ones are written outside it and read through
                        // /api/payment-proofs/{paymentId}; the old path stays denied so the files
                        // already on disk are not still hanging open. Must precede the rule below,
                        // which the first match would otherwise win.
                        .requestMatchers("/api/media/payment-proofs/**").denyAll()
                        .requestMatchers(HttpMethod.GET,  "/api/media/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/locations/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/system-settings/public").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
