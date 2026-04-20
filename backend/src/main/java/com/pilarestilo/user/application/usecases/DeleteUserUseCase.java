package com.pilarestilo.user.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeleteUserUseCase {

    private final UserRepository userRepository;

    public DeleteUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void execute(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("User not found: " + userId);
        }
        try {
            userRepository.deleteById(userId);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("User cannot be deleted because it has related records");
        }
    }
}
