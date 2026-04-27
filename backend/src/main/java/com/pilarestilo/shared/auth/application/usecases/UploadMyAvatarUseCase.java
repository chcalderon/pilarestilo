package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class UploadMyAvatarUseCase {

    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final UserRepository userRepository;
    private final String mediaStoragePath;

    public UploadMyAvatarUseCase(
            UserRepository userRepository,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath
    ) {
        this.userRepository = userRepository;
        this.mediaStoragePath = mediaStoragePath;
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
            Path dir = Paths.get(mediaStoragePath, "users");
            Files.createDirectories(dir);
            Path dest = dir.resolve(userId + ".jpg");
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DomainException("Failed to save avatar");
        }

        String avatarUrl = "/api/media/users/" + userId + ".jpg";
        user.updateAvatarUrl(avatarUrl);
        user.markAvatarAsManual();
        userRepository.save(user);
        return avatarUrl;
    }
}
