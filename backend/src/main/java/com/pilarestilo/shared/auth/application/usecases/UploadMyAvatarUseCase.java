package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class UploadMyAvatarUseCase {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final UserRepository userRepository;
    private final MediaStorageService mediaStorageService;

    public UploadMyAvatarUseCase(UserRepository userRepository, MediaStorageService mediaStorageService) {
        this.userRepository = userRepository;
        this.mediaStorageService = mediaStorageService;
    }

    public String execute(UUID userId, MultipartFile file) {
        if (file.isEmpty()) throw new DomainException("File is empty");
        if (file.getSize() > MAX_BYTES) throw new DomainException("File exceeds 5 MB limit");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new DomainException("Only image files are allowed");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));

        try {
            String url = mediaStorageService.storeRaw(
                    file.getInputStream(), "users", userId + ".jpg", contentType);
            String avatarUrl = url + "?v=" + System.currentTimeMillis();
            user.updateAvatarUrl(avatarUrl);
            user.markAvatarAsManual();
            userRepository.save(user);
            return avatarUrl;
        } catch (IOException e) {
            throw new DomainException("Failed to save avatar");
        }
    }
}
