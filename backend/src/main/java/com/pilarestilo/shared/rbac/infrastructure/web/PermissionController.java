package com.pilarestilo.shared.rbac.infrastructure.web;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.application.usecases.GetPermissionMatrixUseCase;
import com.pilarestilo.shared.rbac.application.usecases.UpdatePermissionMatrixUseCase;
import com.pilarestilo.shared.rbac.infrastructure.web.requests.UpdatePermissionMatrixRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {

    private final GetPermissionMatrixUseCase getMatrixUseCase;
    private final UpdatePermissionMatrixUseCase updateMatrixUseCase;

    public PermissionController(GetPermissionMatrixUseCase getMatrixUseCase,
                                UpdatePermissionMatrixUseCase updateMatrixUseCase) {
        this.getMatrixUseCase = getMatrixUseCase;
        this.updateMatrixUseCase = updateMatrixUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ROLES_READ)")
    public PermissionMatrixDto get() {
        return getMatrixUseCase.execute();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ROLES_MANAGE)")
    public PermissionMatrixDto update(@RequestBody @Valid UpdatePermissionMatrixRequest req) {
        return updateMatrixUseCase.execute(req.permissions());
    }
}
