package com.pilarestilo.privacy.infrastructure.web.controllers;

import com.pilarestilo.privacy.application.dto.DataDeletionRequestDto;
import com.pilarestilo.privacy.application.usecases.ResolveDeletionRequestUseCase;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.privacy.infrastructure.web.requests.RefuseDeletionRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The deletion queue, from the shop's side.
 *
 * <p>Reading it and carrying one out are separate permissions on purpose: anonymising cannot be
 * undone — the person stops being identifiable and no later correction brings them back — so it
 * sits with ADMIN while the desk can see the queue.
 */
@RestController
@RequestMapping("/api/admin/privacy/deletions")
public class AdminPrivacyController {

    private static final String READ =
            "hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PRIVACY_READ)";
    private static final String RESOLVE =
            "hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PRIVACY_RESOLVE)";

    private final DataDeletionRequestRepository deletionRepository;
    private final ResolveDeletionRequestUseCase resolveDeletionRequestUseCase;

    public AdminPrivacyController(DataDeletionRequestRepository deletionRepository,
                                  ResolveDeletionRequestUseCase resolveDeletionRequestUseCase) {
        this.deletionRepository = deletionRepository;
        this.resolveDeletionRequestUseCase = resolveDeletionRequestUseCase;
    }

    /** Oldest first when open: the queue is read by how long somebody has been waiting. */
    @GetMapping
    @PreAuthorize(READ)
    public Page<DataDeletionRequestDto> list(@RequestParam(defaultValue = "true") boolean openOnly,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return (openOnly
                ? deletionRepository.findOpen(pageable)
                : deletionRepository.findAll(pageable))
                .map(DataDeletionRequestDto::from);
    }

    @PostMapping("/{id}/anonymise")
    @PreAuthorize(RESOLVE)
    public DataDeletionRequestDto anonymise(@PathVariable UUID id,
                                            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return DataDeletionRequestDto.from(
                resolveDeletionRequestUseCase.anonymise(id, actorId(currentUser)));
    }

    @PostMapping("/{id}/refuse")
    @PreAuthorize(RESOLVE)
    public DataDeletionRequestDto refuse(@PathVariable UUID id,
                                         @Valid @RequestBody RefuseDeletionRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return DataDeletionRequestDto.from(
                resolveDeletionRequestUseCase.refuse(id, request.reason(), actorId(currentUser)));
    }

    private UUID actorId(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new DomainException("Resolving a deletion request has to record who did it");
        }
        return currentUser.id();
    }
}
