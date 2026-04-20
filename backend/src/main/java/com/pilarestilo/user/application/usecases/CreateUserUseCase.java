package com.pilarestilo.user.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.mappers.UserMapper;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;

    public CreateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserDto execute(String email, String fullName, String role, String passwordHash) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already in use: " + email);
        }
        UserRole userRole = UserRole.valueOf(role);
        User user = User.create(email, fullName, userRole, passwordHash);
        User saved = userRepository.save(user);
        return UserMapper.toDto(saved);
    }
}
