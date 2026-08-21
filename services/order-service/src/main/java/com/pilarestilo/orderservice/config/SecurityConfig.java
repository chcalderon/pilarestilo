package com.pilarestilo.orderservice.config;

import com.pilarestilo.orderservice.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    /*
     * CSRF stays off, and the reason is checked rather than assumed: JwtAuthenticationFilter reads
     * the credential from the Authorization header and from nowhere else, never from a cookie, and
     * the session policy is STATELESS. A form posted by another site therefore arrives
     * unauthenticated however many cookies the browser attaches. The day the filter accepts a
     * cookie, CSRF protection has to come back with it.
     */
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/_health").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
