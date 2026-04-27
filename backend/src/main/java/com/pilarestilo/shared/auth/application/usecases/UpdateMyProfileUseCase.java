package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.UserProfileDto;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UpdateMyProfileUseCase {

    private final UserRepository userRepository;

    public UpdateMyProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserProfileDto execute(UUID userId, String fullName, String phone, String notificationChannelPreference) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        user.updateFullName(fullName);
        user.updatePhone(phone);
        user.updateNotificationChannelPreference(notificationChannelPreference);
        User saved = userRepository.save(user);
        return new UserProfileDto(
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPhone(),
                saved.getNotificationChannelPreference().name(),
                saved.getRole().name(),
                saved.isActive(),
                saved.getAvatarUrl()
        );
    }
}
