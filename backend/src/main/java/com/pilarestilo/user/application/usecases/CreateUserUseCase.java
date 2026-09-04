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
        /* Same normalize-before-check as RegisterUseCase -- otherwise an admin creating a staff
         * account under different letter-casing than an existing one skips this friendly check
         * and hits the DB's unique constraint on save instead. */
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DomainException("Email already in use: " + normalizedEmail);
        }
        UserRole userRole = UserRole.valueOf(role);
        User user = User.create(normalizedEmail, fullName, userRole, passwordHash);
        User saved = userRepository.save(user);
        return UserMapper.toDto(saved);
    }
}
