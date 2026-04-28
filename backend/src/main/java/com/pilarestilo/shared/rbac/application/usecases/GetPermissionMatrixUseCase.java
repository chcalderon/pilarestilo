package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import org.springframework.stereotype.Service;

@Service
public class GetPermissionMatrixUseCase {

    private final RolePermissionRepository repo;

    public GetPermissionMatrixUseCase(RolePermissionRepository repo) { this.repo = repo; }

    public PermissionMatrixDto execute() {
        return PermissionMatrixDto.from(repo.findAll());
    }
}
