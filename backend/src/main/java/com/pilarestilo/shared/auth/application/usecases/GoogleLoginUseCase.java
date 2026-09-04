package com.pilarestilo.shared.auth.application.usecases;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GoogleLoginUseCase {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final String googleClientId;
    private final MediaStorageService mediaStorageService;
    private final RolePermissionResolutionService rolePermissionResolutionService;
    private final String tokenInfoUrl;

    public GoogleLoginUseCase(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper,
            @Value("${app.google.client-id}") String googleClientId,
            MediaStorageService mediaStorageService,
            RolePermissionResolutionService rolePermissionResolutionService,
            @Value("${app.google.tokeninfo-url:https://oauth2.googleapis.com/tokeninfo?id_token=}") String tokenInfoUrl
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.googleClientId = googleClientId;
        this.mediaStorageService = mediaStorageService;
        this.rolePermissionResolutionService = rolePermissionResolutionService;
        this.tokenInfoUrl = tokenInfoUrl;
    }

    public AuthTokenDto execute(String idToken) {
        JsonNode claims = verifyIdToken(idToken);
        validateClaims(claims);

        /* Normalized the same way User.create stores it -- Google's own claim is reliably
         * lowercase in practice, but the account-merge below depends on this matching an
         * existing password account by email, so it is not left to chance. */
        String email = User.normalizeEmail(claims.path("email").asString());
        String fullName = resolveFullName(claims, email);
        String pictureUrl = claims.path("picture").asString(null);

        UserLookupResult lookup = findOrCreateUser(email, fullName, pictureUrl);
        User user = refreshAvatarIfNeeded(lookup.user(), pictureUrl);
        boolean accountMerged = !lookup.isNewUser() && user.getPasswordHash() != null;

        if (!user.isActive()) {
            throw new DomainException("This account is blocked");
        }

        ResolvedPermissions resolvedPermissions = rolePermissionResolutionService.resolve(user.getRole());
        String access = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes(),
                user.getSessionVersion());
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId(), user.getSessionVersion());
        return AuthTokenDto.ofMerged(
                access,
                refresh,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                user.getAvatarUrl(),
                accountMerged,
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes());
    }

    private void validateClaims(JsonNode claims) {
        String emailVerified = claims.path("email_verified").asString();
        String aud = claims.path("aud").asString();
        if (!"true".equalsIgnoreCase(emailVerified)) {
            throw new DomainException("Google account email is not verified");
        }
        if (!aud.equals(googleClientId)) {
            throw new DomainException("Google token audience mismatch");
        }
    }

    private String resolveFullName(JsonNode claims, String email) {
        String fullName = claims.path("name").asString(null);
        if (fullName == null || fullName.isBlank()) {
            return email.split("@")[0];
        }
        return fullName;
    }

    private record UserLookupResult(User user, boolean isNewUser) {}

    private UserLookupResult findOrCreateUser(String email, String fullName, String pictureUrl) {
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return new UserLookupResult(existing.get(), false);
        }

        User newUser = User.create(email, fullName, UserRole.CUSTOMER, null);
        User saved = userRepository.save(newUser);
        if (pictureUrl != null && !pictureUrl.isBlank()) {
            String avatarUrl = downloadAndSaveAvatar(pictureUrl, saved.getId().toString());
            if (avatarUrl != null) {
                saved.updateAvatarUrl(avatarUrl);
                saved = userRepository.save(saved);
            }
        }
        return new UserLookupResult(saved, true);
    }

    private User refreshAvatarIfNeeded(User user, String pictureUrl) {
        if (user.isAvatarManuallySet() || pictureUrl == null || pictureUrl.isBlank()) {
            return user;
        }
        String avatarUrl = downloadAndSaveAvatar(pictureUrl, user.getId().toString());
        if (avatarUrl == null) {
            return user;
        }
        user.updateAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }

    private String downloadAndSaveAvatar(String pictureUrl, String userId) {
        // Closed on the way out: HttpClient holds a selector thread and a connection pool, and one
        // was leaked per Google sign-in.
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(pictureUrl)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) return null;

            String url = mediaStorageService.storeRaw(response.body(), "users", userId + ".jpg", "image/jpeg");
            return url + "?v=" + System.currentTimeMillis();
        } catch (InterruptedException _) {
            // The flag is the only way the caller upstream learns the thread was asked to stop.
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception _) {
            return null;
        }
    }

    private JsonNode verifyIdToken(String idToken) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenInfoUrl + idToken))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DomainException("Invalid Google token");
            }
            return objectMapper.readTree(response.body());
        } catch (DomainException e) {
            throw e;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new DomainException("Failed to verify Google token");
        } catch (Exception _) {
            throw new DomainException("Failed to verify Google token");
        }
    }
}
