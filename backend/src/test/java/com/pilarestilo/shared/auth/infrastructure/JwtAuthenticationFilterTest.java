package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.rbac.application.LegacyViewPermissionMapper;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests written before reducing this filter's Cognitive Complexity (S3776) --
 * it had none. Pin the current, correct behaviour first so the refactor can be verified against it
 * rather than trusted on inspection alone.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock LegacyViewPermissionMapper legacyViewPermissionMapper;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;
    @Mock Claims claims;

    JwtAuthenticationFilter filter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, legacyViewPermissionMapper);
        lenient().when(request.getRequestURI()).thenReturn("/api/products");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeaderLeavesTheRequestAnonymous() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aHeaderWithoutTheBearerPrefixIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anInvalidTokenLeavesTheRequestAnonymous() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtTokenProvider.isValid("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aTokenForAUserThatNoLongerExistsLeavesTheRequestAnonymous() throws Exception {
        validToken();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aBlockedUserIsNotAuthenticatedEvenWithAValidToken() throws Exception {
        validToken();
        User blocked = User.reconstruct(userId, "ana@correo.cl", "Ana", UserRole.CUSTOMER, false, "hash", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(blocked));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anActiveUserWithPermissionCodesIsAuthenticatedWithoutTheLegacyFallback() throws Exception {
        validToken();
        activeUser();
        when(claims.get("permissions", List.class)).thenReturn(List.of("productos"));
        when(claims.get("permissionCodes", List.class)).thenReturn(List.of("products.read"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.permissionCodes()).containsExactly("products.read");
        verify(legacyViewPermissionMapper, never()).toPermissionCodes(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anOlderTokenWithNoPermissionCodesFallsBackToTheLegacyMapping() throws Exception {
        validToken();
        activeUser();
        when(claims.get("permissions", List.class)).thenReturn(List.of("productos"));
        when(claims.get("permissionCodes", List.class)).thenReturn(null);
        when(legacyViewPermissionMapper.toPermissionCodes(List.of("productos")))
                .thenReturn(List.of("products.read", "products.update"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.permissionCodes()).containsExactly("products.read", "products.update");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aTokenWhoseSessionVersionIsBehindTheUsersIsRejected() throws Exception {
        validToken();
        User user = User.reconstruct(userId, "ana@correo.cl", "Ana", UserRole.CUSTOMER, true, "hash", Instant.now());
        user.setSessionVersion(3);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(claims.get("sv", Integer.class)).thenReturn(2);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aTokenWithNoSvClaimIsTreatedAsVersion1AndStillAuthenticates() throws Exception {
        validToken();
        activeUser();
        when(claims.get("permissions", List.class)).thenReturn(List.of("productos"));
        when(claims.get("permissionCodes", List.class)).thenReturn(List.of("products.read"));
        when(claims.get("sv", Integer.class)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void aTokenWhoseSessionVersionMatchesTheUsersAuthenticates() throws Exception {
        validToken();
        User user = User.reconstruct(userId, "ana@correo.cl", "Ana", UserRole.CUSTOMER, true, "hash", Instant.now());
        user.setSessionVersion(5);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(claims.get("email", String.class)).thenReturn("ana@correo.cl");
        lenient().when(claims.get("role", String.class)).thenReturn("CUSTOMER");
        when(claims.get("permissions", List.class)).thenReturn(List.of("productos"));
        when(claims.get("permissionCodes", List.class)).thenReturn(List.of("products.read"));
        when(claims.get("sv", Integer.class)).thenReturn(5);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    private void validToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        when(jwtTokenProvider.parseToken("good-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
    }

    private void activeUser() {
        User user = User.reconstruct(userId, "ana@correo.cl", "Ana", UserRole.CUSTOMER, true, "hash", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(claims.get("email", String.class)).thenReturn("ana@correo.cl");
        lenient().when(claims.get("role", String.class)).thenReturn("CUSTOMER");
        lenient().when(claims.get("sv", Integer.class)).thenReturn(1);
    }
}
