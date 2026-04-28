package com.pilarestilo.shared.rbac.application.dto;

import com.pilarestilo.user.domain.model.User;

import java.time.LocalDate;
import java.util.UUID;

public record WorkerDto(
        UUID id,
        String email,
        String fullName,
        String role,
        LocalDate vigencyStart,
        LocalDate vigencyEnd,
        boolean active
) {
    public static WorkerDto from(User user) {
        return new WorkerDto(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(),
                user.getWorkerVigencyStart(), user.getWorkerVigencyEnd(),
                user.isActive());
    }
}
