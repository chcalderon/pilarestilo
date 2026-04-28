package com.pilarestilo.shared.rbac.infrastructure.web.requests;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePermissionMatrixRequest(@NotNull List<PermissionMatrixDto.PermissionEntryDto> permissions) {}
