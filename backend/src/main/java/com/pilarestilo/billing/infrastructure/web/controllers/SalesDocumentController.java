package com.pilarestilo.billing.infrastructure.web.controllers;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.usecases.GetSalesDocumentsForOrderUseCase;
import com.pilarestilo.billing.application.usecases.IssueCreditNoteUseCase;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.application.usecases.AttachDocumentFileUseCase;
import com.pilarestilo.billing.application.usecases.IssueSalesDocumentUseCase;
import com.pilarestilo.billing.application.usecases.ReissueSalesDocumentUseCase;
import com.pilarestilo.billing.application.usecases.VoidSalesDocumentUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.billing.infrastructure.storage.SalesDocumentFileStorage;
import com.pilarestilo.billing.infrastructure.web.requests.IssueCreditNoteRequest;
import com.pilarestilo.billing.infrastructure.web.requests.IssueSalesDocumentRequest;
import com.pilarestilo.billing.infrastructure.web.requests.ReissueSalesDocumentRequest;
import com.pilarestilo.billing.infrastructure.web.requests.VoidSalesDocumentRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sales-documents")
public class SalesDocumentController {

    private final IssueSalesDocumentUseCase issueSalesDocumentUseCase;
    private final VoidSalesDocumentUseCase voidSalesDocumentUseCase;
    private final ReissueSalesDocumentUseCase reissueSalesDocumentUseCase;
    private final GetSalesDocumentsForOrderUseCase getSalesDocumentsForOrderUseCase;
    private final SalesDocumentRepository salesDocumentRepository;
    private final IssueCreditNoteUseCase issueCreditNoteUseCase;
    private final SalesDocumentFileStorage fileStorage;
    private final AttachDocumentFileUseCase attachDocumentFileUseCase;

    public SalesDocumentController(IssueSalesDocumentUseCase issueSalesDocumentUseCase,
                                   VoidSalesDocumentUseCase voidSalesDocumentUseCase,
                                   ReissueSalesDocumentUseCase reissueSalesDocumentUseCase,
                                   GetSalesDocumentsForOrderUseCase getSalesDocumentsForOrderUseCase,
                                   SalesDocumentRepository salesDocumentRepository,
                                   IssueCreditNoteUseCase issueCreditNoteUseCase,
                                   SalesDocumentFileStorage fileStorage,
                                   AttachDocumentFileUseCase attachDocumentFileUseCase) {
        this.issueSalesDocumentUseCase = issueSalesDocumentUseCase;
        this.voidSalesDocumentUseCase = voidSalesDocumentUseCase;
        this.reissueSalesDocumentUseCase = reissueSalesDocumentUseCase;
        this.getSalesDocumentsForOrderUseCase = getSalesDocumentsForOrderUseCase;
        this.salesDocumentRepository = salesDocumentRepository;
        this.issueCreditNoteUseCase = issueCreditNoteUseCase;
        this.fileStorage = fileStorage;
        this.attachDocumentFileUseCase = attachDocumentFileUseCase;
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
                request.fileUrl(),
                actorId(currentUser));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_READ)")
    public List<SalesDocumentDto> byOrder(@PathVariable UUID orderId) {
        return getSalesDocumentsForOrderUseCase.execute(orderId);
    }

    /**
     * Uploads the boleta file and returns the opaque name to send back on issue. Kept separate from
     * issuing because the folio is typed the moment the boleta is emitted while the PDF often
     * arrives later.
     */
    @PostMapping("/files")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_ISSUE)")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("fileUrl", fileStorage.store(file));
    }

    /**
     * Files the picture of a boleta that was registered without one. Guarded by the issue
     * permission rather than a new one: whoever may register the document may finish filing it.
     */
    @PostMapping("/{id}/file")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_ISSUE)")
    public SalesDocumentDto attachFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return SalesDocumentMapper.toDto(
                attachDocumentFileUseCase.execute(id, fileStorage.store(file)));
    }

    /**
     * Streams the stored file. This is the only way to read one: the files live outside the media
     * root precisely because {@code /api/media/**} is public.
     */
    @GetMapping("/{id}/file")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_READ)")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        SalesDocument document = salesDocumentRepository.findById(id)
                .orElseThrow(() -> new DomainException("Sales document not found: " + id));
        String stored = document.getFileUrl();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, fileStorage.contentTypeOf(stored))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getType().name().toLowerCase(Locale.ROOT)
                                + "-" + document.getFolio() + "\"")
                .body(new FileSystemResource(fileStorage.resolve(stored)));
    }

    private SalesDocumentType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return SalesDocumentType.BOLETA;
        }
        try {
            return SalesDocumentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
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
