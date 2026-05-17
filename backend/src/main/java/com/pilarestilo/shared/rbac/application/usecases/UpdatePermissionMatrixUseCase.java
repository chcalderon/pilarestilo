package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UpdatePermissionMatrixUseCase {

    private final RolePermissionRepository repo;
    private final RolePermissionResolutionService resolutionService;

    public UpdatePermissionMatrixUseCase(RolePermissionRepository repo,
                                         RolePermissionResolutionService resolutionService) {
        this.repo = repo;
        this.resolutionService = resolutionService;
    }

    public PermissionMatrixDto execute(List<PermissionMatrixDto.PermissionEntryDto> entries) {
        List<RolePermission> perms = entries.stream()
                .map(e -> new RolePermission(UserRole.valueOf(e.role()), e.viewKey()))
                .toList();
        repo.replaceAll(perms);
        resolutionService.invalidateAll();
        return PermissionMatrixDto.from(repo.findAll());
    }
}
