package com.pilarestilo.user.application.usecases;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.mappers.UserMapper;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListUsersUseCase {

    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> execute(Pageable pageable, String role, Boolean active) {
        if (role != null && !role.isBlank()) {
            UserRole parsedRole = UserRole.valueOf(role.trim().toUpperCase());
            return userRepository.findByRoleAndActive(parsedRole, active, pageable).map(UserMapper::toDto);
        }

        if (active != null) {
            return userRepository.findByActive(active, pageable).map(UserMapper::toDto);
        }

        return userRepository.findAll(pageable).map(UserMapper::toDto);
    }
}
