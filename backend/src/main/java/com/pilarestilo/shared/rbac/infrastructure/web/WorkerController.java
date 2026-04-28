package com.pilarestilo.shared.rbac.infrastructure.web;

import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.shared.rbac.application.usecases.AssignWorkerUseCase;
import com.pilarestilo.shared.rbac.application.usecases.ListWorkersUseCase;
import com.pilarestilo.shared.rbac.application.usecases.RevokeWorkerUseCase;
import com.pilarestilo.shared.rbac.infrastructure.web.requests.AssignWorkerRequest;
import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/workers")
public class WorkerController {

    private final AssignWorkerUseCase assignWorkerUseCase;
    private final RevokeWorkerUseCase revokeWorkerUseCase;
    private final ListWorkersUseCase listWorkersUseCase;

    public WorkerController(AssignWorkerUseCase assignWorkerUseCase,
                            RevokeWorkerUseCase revokeWorkerUseCase,
                            ListWorkersUseCase listWorkersUseCase) {
        this.assignWorkerUseCase = assignWorkerUseCase;
        this.revokeWorkerUseCase = revokeWorkerUseCase;
        this.listWorkersUseCase = listWorkersUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<WorkerDto> list(Pageable pageable) {
        return listWorkersUseCase.execute(pageable);
    }

    @PostMapping("/{userId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkerDto assign(@PathVariable UUID userId,
                            @RequestBody @Valid AssignWorkerRequest req) {
        return assignWorkerUseCase.execute(userId, UserRole.valueOf(req.role()),
                req.vigencyStart(), req.vigencyEnd());
    }

    @DeleteMapping("/{userId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void revoke(@PathVariable UUID userId) {
        revokeWorkerUseCase.execute(userId);
    }
}
