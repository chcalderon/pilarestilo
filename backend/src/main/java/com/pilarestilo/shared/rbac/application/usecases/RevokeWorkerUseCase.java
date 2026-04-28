package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RevokeWorkerUseCase {

    private final UserRepository userRepository;

    public RevokeWorkerUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.changeRole(UserRole.CUSTOMER);
        user.setWorkerVigencyStart(null);
        user.setWorkerVigencyEnd(null);
        userRepository.save(user);
    }
}
