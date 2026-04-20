package com.pilarestilo.user.application.usecases;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.mappers.UserMapper;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetUserUseCase {

    private final UserRepository userRepository;

    public GetUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserDto execute(UUID id) {
        return userRepository.findById(id)
                .map(UserMapper::toDto)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }
}
