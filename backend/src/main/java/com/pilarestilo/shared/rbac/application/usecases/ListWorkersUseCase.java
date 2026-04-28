package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListWorkersUseCase {

    private final UserRepository userRepository;

    public ListWorkersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<WorkerDto> execute(Pageable pageable) {
        List<UserRole> workerRoles = List.of(
                UserRole.SUPERVISOR, UserRole.ADMINISTRACION,
                UserRole.DESPACHADOR, UserRole.SELLER);
        return userRepository.findByRoleIn(workerRoles, pageable).map(WorkerDto::from);
    }
}
