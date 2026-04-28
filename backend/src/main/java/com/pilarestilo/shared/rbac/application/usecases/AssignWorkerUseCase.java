package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AssignWorkerUseCase {

    private final UserRepository userRepository;

    public AssignWorkerUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public WorkerDto execute(UUID userId, UserRole role, LocalDate vigencyStart, LocalDate vigencyEnd) {
        if (role == UserRole.ADMIN || role == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Cannot assign ADMIN or CUSTOMER as worker role via this endpoint");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.changeRole(role);
        user.setWorkerVigencyStart(vigencyStart);
        user.setWorkerVigencyEnd(vigencyEnd);
        return WorkerDto.from(userRepository.save(user));
    }
}
