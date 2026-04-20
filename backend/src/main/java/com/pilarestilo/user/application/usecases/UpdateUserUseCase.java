package com.pilarestilo.user.application.usecases;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.mappers.UserMapper;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UpdateUserUseCase {

    private final UserRepository userRepository;

    public UpdateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserDto execute(UUID userId, String fullName, String role, Boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        if (fullName != null) {
            user.updateFullName(fullName);
        }

        if (role != null && !role.isBlank()) {
            user.changeRole(UserRole.valueOf(role.trim().toUpperCase()));
        }

        if (active != null) {
            user.setActive(active);
        }

        User saved = userRepository.save(user);
        return UserMapper.toDto(saved);
    }
}
