package com.pilarestilo.returns.infrastructure.web.controllers;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.usecases.ListReturnsUseCase;
import com.pilarestilo.returns.application.usecases.ManageReturnUseCase;
import com.pilarestilo.returns.application.usecases.RegisterRefundUseCase;
import com.pilarestilo.returns.application.usecases.RequestReturnUseCase;
import com.pilarestilo.returns.application.usecases.ResolveItemDispositionUseCase;
import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.infrastructure.web.requests.ReturnRequests;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/returns")
public class ReturnController {

    private static final String READ =
            "hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).RETURNS_READ)";
    private static final String MANAGE =
            "hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).RETURNS_MANAGE)";
    private static final String REFUND =
            "hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).RETURNS_REFUND)";

    private final RequestReturnUseCase requestReturnUseCase;
    private final ManageReturnUseCase manageReturnUseCase;
    private final ResolveItemDispositionUseCase resolveItemDispositionUseCase;
    private final RegisterRefundUseCase registerRefundUseCase;
    private final ListReturnsUseCase listReturnsUseCase;

    public ReturnController(RequestReturnUseCase requestReturnUseCase,
                            ManageReturnUseCase manageReturnUseCase,
                            ResolveItemDispositionUseCase resolveItemDispositionUseCase,
                            RegisterRefundUseCase registerRefundUseCase,
                            ListReturnsUseCase listReturnsUseCase) {
        this.requestReturnUseCase = requestReturnUseCase;
        this.manageReturnUseCase = manageReturnUseCase;
        this.resolveItemDispositionUseCase = resolveItemDispositionUseCase;
        this.registerRefundUseCase = registerRefundUseCase;
        this.listReturnsUseCase = listReturnsUseCase;
    }

    /** Open ones first by deadline: a legal countdown only makes sense ordered by how little is left. */
    @GetMapping
    @PreAuthorize(READ)
    public Page<ReturnRequestDto> list(@RequestParam(defaultValue = "true") boolean openOnly,
                                       Pageable pageable) {
        return listReturnsUseCase.execute(openOnly, pageable);
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize(READ)
    public List<ReturnRequestDto> byOrder(@PathVariable UUID orderId) {
        return listReturnsUseCase.byOrder(orderId);
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ReturnRequestDto> open(@Valid @RequestBody ReturnRequests.Open request) {
        ReturnRequestDto created = requestReturnUseCase.execute(
                request.orderId(), parseKind(request.kind()), request.reason(), null, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(MANAGE)
    public ReturnRequestDto approve(@PathVariable UUID id) {
        return manageReturnUseCase.approve(id);
    }

    /** Refuses a devolucion. A retracto inside its window is refused by the aggregate, not here. */
    @PostMapping("/{id}/reject")
    @PreAuthorize(MANAGE)
    public ReturnRequestDto reject(@PathVariable UUID id,
                                   @Valid @RequestBody ReturnRequests.Reject request) {
        return manageReturnUseCase.reject(id, request.note());
    }

    /** The garment arrived. It goes into reconditioning; no stock moves here. */
    @PostMapping("/{id}/receive")
    @PreAuthorize(MANAGE)
    public ReturnRequestDto receive(@PathVariable UUID id) {
        return manageReturnUseCase.receive(id);
    }

    @PostMapping("/{id}/disposition")
    @PreAuthorize(MANAGE)
    public ReturnRequestDto disposition(@PathVariable UUID id,
                                        @Valid @RequestBody ReturnRequests.Disposition request) {
        return resolveItemDispositionUseCase.execute(
                id, parseDisposition(request.disposition()), request.note());
    }

    @PostMapping("/{id}/bank-account")
    @PreAuthorize(REFUND)
    public ReturnRequestDto bankAccount(@PathVariable UUID id,
                                        @Valid @RequestBody ReturnRequests.BankAccount request) {
        return registerRefundUseCase.attachAccount(id, request.holder(), request.rut(),
                request.bankName(), request.accountType(), request.accountNumber());
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize(REFUND)
    public ReturnRequestDto refund(@PathVariable UUID id,
                                   @Valid @RequestBody ReturnRequests.Refund request) {
        return registerRefundUseCase.execute(id, request.amount(), request.currency(),
                parseMethod(request.method()), request.reference(), request.fileUrl());
    }

    private ReturnKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReturnKind.DEVOLUCION;
        }
        try {
            return ReturnKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unknown return kind: " + raw);
        }
    }

    private ItemDisposition parseDisposition(String raw) {
        try {
            return ItemDisposition.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unknown disposition: " + raw);
        }
    }

    private RefundMethod parseMethod(String raw) {
        try {
            return RefundMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unknown refund method: " + raw);
        }
    }
}
