package com.pilarestilo.billing.infrastructure.web.controllers;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.usecases.GetSalesDocumentsForOrderUseCase;
import com.pilarestilo.billing.application.usecases.IssueCreditNoteUseCase;
import com.pilarestilo.billing.application.usecases.IssueSalesDocumentUseCase;
import com.pilarestilo.billing.application.usecases.ReissueSalesDocumentUseCase;
import com.pilarestilo.billing.application.usecases.VoidSalesDocumentUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.infrastructure.web.requests.IssueCreditNoteRequest;
import com.pilarestilo.billing.infrastructure.web.requests.IssueSalesDocumentRequest;
import com.pilarestilo.billing.infrastructure.web.requests.ReissueSalesDocumentRequest;
import com.pilarestilo.billing.infrastructure.web.requests.VoidSalesDocumentRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sales-documents")
public class SalesDocumentController {

    private final IssueSalesDocumentUseCase issueSalesDocumentUseCase;
    private final VoidSalesDocumentUseCase voidSalesDocumentUseCase;
    private final ReissueSalesDocumentUseCase reissueSalesDocumentUseCase;
    private final GetSalesDocumentsForOrderUseCase getSalesDocumentsForOrderUseCase;
    private final IssueCreditNoteUseCase issueCreditNoteUseCase;

    public SalesDocumentController(IssueSalesDocumentUseCase issueSalesDocumentUseCase,
                                   VoidSalesDocumentUseCase voidSalesDocumentUseCase,
                                   ReissueSalesDocumentUseCase reissueSalesDocumentUseCase,
                                   GetSalesDocumentsForOrderUseCase getSalesDocumentsForOrderUseCase,
                                   IssueCreditNoteUseCase issueCreditNoteUseCase) {
        this.issueSalesDocumentUseCase = issueSalesDocumentUseCase;
        this.voidSalesDocumentUseCase = voidSalesDocumentUseCase;
        this.reissueSalesDocumentUseCase = reissueSalesDocumentUseCase;
        this.getSalesDocumentsForOrderUseCase = getSalesDocumentsForOrderUseCase;
        this.issueCreditNoteUseCase = issueCreditNoteUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_ISSUE)")
    public ResponseEntity<SalesDocumentDto> issue(@Valid @RequestBody IssueSalesDocumentRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        SalesDocumentDto created = issueSalesDocumentUseCase.execute(
                request.orderId(),
                parseType(request.documentType()),
                request.folio(),
                request.receiverRut(),
                request.receiverBusinessName(),
                request.receiverBusinessActivity(),
                request.fileUrl(),
                actorId(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Registers a nota de credito against the sale's live document. Separate from issuing, because
     * what it validates is different: not that the sale can be documented, but that the document it
     * undoes exists and has not already been credited in full.
     */
    @PostMapping("/credit-notes")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_ISSUE)")
    public ResponseEntity<SalesDocumentDto> creditNote(@Valid @RequestBody IssueCreditNoteRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        SalesDocumentDto created = issueCreditNoteUseCase.execute(
                request.orderId(),
                request.folio(),
                request.amount(),
                request.fileUrl(),
                request.returnId(),
                actorId(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_VOID)")
    public SalesDocumentDto voidDocument(@PathVariable UUID id,
                                         @Valid @RequestBody VoidSalesDocumentRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return voidSalesDocumentUseCase.execute(id, request.reason(), actorId(currentUser));
    }

    @PostMapping("/{id}/reissue")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_ISSUE)")
    public SalesDocumentDto reissue(@PathVariable UUID id,
                                    @Valid @RequestBody ReissueSalesDocumentRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return reissueSalesDocumentUseCase.execute(
                id,
                request.voidReason(),
                parseType(request.documentType()),
                request.folio(),
                request.receiverRut(),
                request.receiverBusinessName(),
                request.receiverBusinessActivity(),
                request.fileUrl(),
                actorId(currentUser));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_READ)")
    public List<SalesDocumentDto> byOrder(@PathVariable UUID orderId) {
        return getSalesDocumentsForOrderUseCase.execute(orderId);
    }

    private SalesDocumentType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return SalesDocumentType.BOLETA;
        }
        try {
            return SalesDocumentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unknown document type: " + raw);
        }
    }

    private UUID actorId(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new DomainException("A tax document has to record who registered it");
        }
        return currentUser.id();
    }
}
