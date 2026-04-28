package com.pilarestilo.shared.auth.application.usecases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
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
import java.util.List;

@Service
public class GoogleLoginUseCase {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final String googleClientId;
    private final MediaStorageService mediaStorageService;
    private final RolePermissionRepository rolePermissionRepository;

    public GoogleLoginUseCase(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper,
            @Value("${app.google.client-id}") String googleClientId,
            MediaStorageService mediaStorageService,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.googleClientId = googleClientId;
        this.mediaStorageService = mediaStorageService;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public AuthTokenDto execute(String idToken) {
        JsonNode claims = verifyIdToken(idToken);

        String email = claims.path("email").asText();
        String emailVerified = claims.path("email_verified").asText();
        String aud = claims.path("aud").asText();

        if (!"true".equalsIgnoreCase(emailVerified)) {
            throw new DomainException("Google account email is not verified");
        }
        if (!aud.equals(googleClientId)) {
            throw new DomainException("Google token audience mismatch");
        }

        String fullName = claims.path("name").asText(null);
        if (fullName == null || fullName.isBlank()) {
            fullName = email.split("@")[0];
        }
        String pictureUrl = claims.path("picture").asText(null);

        String finalFullName = fullName;
        String finalPictureUrl = pictureUrl;

        boolean[] isNewUser = {false};
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            isNewUser[0] = true;
            User newUser = User.create(email, finalFullName, UserRole.CUSTOMER, null);
            User saved = userRepository.save(newUser);
            if (finalPictureUrl != null && !finalPictureUrl.isBlank()) {
                String avatarUrl = downloadAndSaveAvatar(finalPictureUrl, saved.getId().toString());
                if (avatarUrl != null) {
                    saved.updateAvatarUrl(avatarUrl);
                    return userRepository.save(saved);
                }
            }
            return saved;
        });

        boolean accountMerged = !isNewUser[0] && user.getPasswordHash() != null;

        if (!user.isAvatarManuallySet() && finalPictureUrl != null && !finalPictureUrl.isBlank()) {
            String avatarUrl = downloadAndSaveAvatar(finalPictureUrl, user.getId().toString());
            if (avatarUrl != null) {
                user.updateAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            }
        }

        if (!user.isActive()) {
            throw new DomainException("This account is blocked");
        }

        List<String> permissions = rolePermissionRepository.findViewKeysByRole(user.getRole());
        String access = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.ofMerged(access, refresh, user.getId(), user.getEmail(), user.getRole().name(), user.getFullName(), user.getAvatarUrl(), accountMerged, permissions);
    }

    private String downloadAndSaveAvatar(String pictureUrl, String userId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(pictureUrl)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) return null;

            String url = mediaStorageService.storeRaw(response.body(), "users", userId + ".jpg", "image/jpeg");
            return url + "?v=" + System.currentTimeMillis();
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode verifyIdToken(String idToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENINFO_URL + idToken))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DomainException("Invalid Google token");
            }
            return objectMapper.readTree(response.body());
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Failed to verify Google token");
        }
    }
}
