package com.pilarestilo.orderservice.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private Claims claims;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticates_internal_call_using_service_token() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, "internal-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Service-Token", "internal-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertNotNull(principal);
        assertTrue(principal.internalCall());
        assertEquals(UserRole.ADMIN, principal.role());
    }

    @Test
    void authenticates_bearer_token_when_valid() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        UUID userId = UUID.randomUUID();

        when(jwtTokenProvider.isValid("jwt-token")).thenReturn(true);
        when(jwtTokenProvider.parseToken("jwt-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("email", String.class)).thenReturn("seller@pilarestilo.com");
        when(claims.get("role", String.class)).thenReturn("SELLER");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertNotNull(principal);
        assertEquals(userId, principal.id());
        assertEquals(UserRole.SELLER, principal.role());
        assertFalse(principal.internalCall());
    }

    @Test
    void leaves_context_empty_when_token_is_invalid() throws ServletException, IOException {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        when(jwtTokenProvider.isValid("invalid")).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }
}
