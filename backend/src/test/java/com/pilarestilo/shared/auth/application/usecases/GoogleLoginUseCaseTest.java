package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests written before reducing execute()'s Cognitive Complexity (S3776) -- it
 * had none, despite touching auth. A real local HttpServer stands in for both Google's tokeninfo
 * endpoint (now injectable via app.google.tokeninfo-url, added for exactly this) and the avatar
 * picture URL Google returns, since the use case opens its own java.net.http.HttpClient with no
 * other seam.
 */
class GoogleLoginUseCaseTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    private HttpServer server;
    private final AtomicInteger avatarRequests = new AtomicInteger();

    UserRepository userRepository;
    JwtTokenProvider jwtTokenProvider;
    MediaStorageService mediaStorageService;
    RolePermissionResolutionService rolePermissionResolutionService;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GoogleLoginUseCase buildUseCase(Map<String, Object> tokenInfoClaims) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/tokeninfo", exchange -> respondJson(exchange, tokenInfoClaims));
        server.createContext("/avatar.jpg", exchange -> {
            avatarRequests.incrementAndGet();
            respondBytes(exchange, "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();

        userRepository = mock(UserRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        mediaStorageService = mock(MediaStorageService.class);
        rolePermissionResolutionService = mock(RolePermissionResolutionService.class);

        lenient().when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn("access-token");
        lenient().when(jwtTokenProvider.generateRefreshToken(any(), anyInt())).thenReturn("refresh-token");
        lenient().when(rolePermissionResolutionService.resolve(any()))
                .thenReturn(new ResolvedPermissions(List.of("legacyView"), List.of("perm.code")));
        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mediaStorageService.storeRaw(any(), any(), any(), any()))
                .thenReturn("/media/users/avatar.jpg");

        return new GoogleLoginUseCase(
                userRepository, jwtTokenProvider, new ObjectMapper(), CLIENT_ID,
                mediaStorageService, rolePermissionResolutionService,
                baseUrl + "/tokeninfo?id_token=");
    }

    private void respondJson(HttpExchange exchange, Map<String, Object> claims) throws IOException {
        byte[] bytes = new ObjectMapper().writeValueAsString(claims).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void respondBytes(HttpExchange exchange, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, Object> validClaims(String email, String name, String picture) {
        return Map.of(
                "email", email,
                "email_verified", "true",
                "aud", CLIENT_ID,
                "name", name,
                "picture", picture);
    }

    @Test
    void unverifiedEmailIsRejected() throws IOException {
        var useCase = buildUseCase(Map.of(
                "email", "a@b.com", "email_verified", "false", "aud", CLIENT_ID, "name", "A"));

        assertThatThrownBy(() -> useCase.execute("token"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void audienceMismatchIsRejected() throws IOException {
        var useCase = buildUseCase(Map.of(
                "email", "a@b.com", "email_verified", "true", "aud", "someone-else", "name", "A"));

        assertThatThrownBy(() -> useCase.execute("token"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("audience mismatch");
    }

    @Test
    void aNonOkTokeninfoResponseIsInvalidToken() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/tokeninfo", exchange -> {
            exchange.sendResponseHeaders(400, -1);
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        userRepository = mock(UserRepository.class);
        var useCase = new GoogleLoginUseCase(
                userRepository, mock(JwtTokenProvider.class), new ObjectMapper(), CLIENT_ID,
                mock(MediaStorageService.class), mock(RolePermissionResolutionService.class),
                baseUrl + "/tokeninfo?id_token=");

        assertThatThrownBy(() -> useCase.execute("token"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Invalid Google token");
    }

    @Test
    void createsANewUserAndDownloadsTheAvatarTwiceViaTheExistingPostCreationRefreshCheck() throws IOException {
        var useCase = buildUseCase(validClaims("new@pilarestilo.com", "Nueva Usuaria", "PLACEHOLDER"));
        // picture url is filled in per-test since it must point at this test's own server port
        var claims = validClaims("new@pilarestilo.com", "Nueva Usuaria",
                "http://localhost:" + server.getAddress().getPort() + "/avatar.jpg");
        server.removeContext("/tokeninfo");
        server.createContext("/tokeninfo", exchange -> respondJson(exchange, claims));

        when(userRepository.findByEmail("new@pilarestilo.com")).thenReturn(Optional.empty());

        var result = useCase.execute("token");

        assertThat(result.email()).isEqualTo("new@pilarestilo.com");
        assertThat(result.fullName()).isEqualTo("Nueva Usuaria");
        assertThat(result.accountMerged()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.permissionCodes()).containsExactly("perm.code");
        // Pinning existing (unintended) behaviour: updateAvatarUrl doesn't mark the avatar as
        // manually set, so the post-creation refresh check re-downloads it a second time --
        // saving three times total: the initial creation, the in-lambda avatar update, and the
        // post-creation refresh's avatar update.
        assertThat(avatarRequests.get()).isEqualTo(2);
        verify(userRepository, times(3)).save(any());
    }

    @Test
    void derivesFullNameFromEmailWhenGoogleOmitsIt() throws IOException {
        Map<String, Object> claims = Map.of(
                "email", "sinnombre@pilarestilo.com", "email_verified", "true", "aud", CLIENT_ID);
        var useCase = buildUseCase(claims);
        when(userRepository.findByEmail("sinnombre@pilarestilo.com")).thenReturn(Optional.empty());

        var result = useCase.execute("token");

        assertThat(result.fullName()).isEqualTo("sinnombre");
    }

    @Test
    void existingUserWithAPasswordIsFlaggedAsAnAccountMerge() throws IOException {
        var useCase = buildUseCase(validClaims("existente@pilarestilo.com", "Existente", ""));
        User existing = User.create("existente@pilarestilo.com", "Existente", UserRole.CUSTOMER, "some-hash");
        existing.setAvatarManuallySet(true);
        when(userRepository.findByEmail("existente@pilarestilo.com")).thenReturn(Optional.of(existing));

        var result = useCase.execute("token");

        assertThat(result.accountMerged()).isTrue();
    }

    @Test
    void existingUserWithoutAPasswordIsNotAnAccountMerge() throws IOException {
        var useCase = buildUseCase(validClaims("solo-google@pilarestilo.com", "Solo Google", ""));
        User existing = User.create("solo-google@pilarestilo.com", "Solo Google", UserRole.CUSTOMER, null);
        existing.setAvatarManuallySet(true);
        when(userRepository.findByEmail("solo-google@pilarestilo.com")).thenReturn(Optional.of(existing));

        var result = useCase.execute("token");

        assertThat(result.accountMerged()).isFalse();
    }

    @Test
    void existingUserWithAManuallySetAvatarIsNeverRefreshedFromGoogle() throws IOException {
        var claimsPicture = "will-be-set-below";
        var useCase = buildUseCase(validClaims("manual@pilarestilo.com", "Manual", claimsPicture));
        var claims = validClaims("manual@pilarestilo.com", "Manual",
                "http://localhost:" + server.getAddress().getPort() + "/avatar.jpg");
        server.removeContext("/tokeninfo");
        server.createContext("/tokeninfo", exchange -> respondJson(exchange, claims));

        User existing = User.create("manual@pilarestilo.com", "Manual", UserRole.CUSTOMER, null);
        existing.setAvatarManuallySet(true);
        when(userRepository.findByEmail("manual@pilarestilo.com")).thenReturn(Optional.of(existing));

        useCase.execute("token");

        assertThat(avatarRequests.get()).isZero();
        verify(userRepository, times(0)).save(any());
    }

    @Test
    void existingUserWithoutAManuallySetAvatarIsRefreshedFromGoogle() throws IOException {
        var useCase = buildUseCase(validClaims("refresh@pilarestilo.com", "Refresh", "placeholder"));
        var claims = validClaims("refresh@pilarestilo.com", "Refresh",
                "http://localhost:" + server.getAddress().getPort() + "/avatar.jpg");
        server.removeContext("/tokeninfo");
        server.createContext("/tokeninfo", exchange -> respondJson(exchange, claims));

        User existing = User.create("refresh@pilarestilo.com", "Refresh", UserRole.CUSTOMER, null);
        when(userRepository.findByEmail("refresh@pilarestilo.com")).thenReturn(Optional.of(existing));

        useCase.execute("token");

        assertThat(avatarRequests.get()).isEqualTo(1);
        verify(userRepository, times(1)).save(existing);
        assertThat(existing.getAvatarUrl()).startsWith("/media/users/avatar.jpg?v=");
    }

    @Test
    void aBlockedUserCannotLogIn() throws IOException {
        var useCase = buildUseCase(validClaims("blocked@pilarestilo.com", "Blocked", ""));
        User existing = User.create("blocked@pilarestilo.com", "Blocked", UserRole.CUSTOMER, null);
        existing.setAvatarManuallySet(true);
        existing.setActive(false);
        when(userRepository.findByEmail("blocked@pilarestilo.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute("token"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("blocked");
    }
}
