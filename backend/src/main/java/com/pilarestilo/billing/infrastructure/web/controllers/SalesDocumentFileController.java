package com.pilarestilo.billing.infrastructure.web.controllers;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.application.usecases.AttachDocumentFileUseCase;
import com.pilarestilo.billing.application.usecases.GetSalesDocumentUseCase;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.infrastructure.storage.SalesDocumentFileStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The picture of the paper, which is a separate concern from the document it belongs to: the folio
 * is typed the moment the boleta is emitted in the SII app, while the scan often arrives later, and
 * a file can be filed against a document that was registered without one.
 *
 * <p>Shares the base path with {@link SalesDocumentController} so the URLs the admin already calls
 * do not move.
 */
@RestController
@RequestMapping("/api/admin/sales-documents")
public class SalesDocumentFileController {

    private final SalesDocumentFileStorage fileStorage;
    private final AttachDocumentFileUseCase attachDocumentFileUseCase;
    private final GetSalesDocumentUseCase getSalesDocumentUseCase;

    public SalesDocumentFileController(SalesDocumentFileStorage fileStorage,
                                       AttachDocumentFileUseCase attachDocumentFileUseCase,
                                       GetSalesDocumentUseCase getSalesDocumentUseCase) {
        this.fileStorage = fileStorage;
        this.attachDocumentFileUseCase = attachDocumentFileUseCase;
        this.getSalesDocumentUseCase = getSalesDocumentUseCase;
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
        SalesDocument document = getSalesDocumentUseCase.execute(id);
        String stored = document.getFileUrl();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, fileStorage.contentTypeOf(stored))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getType().name().toLowerCase(Locale.ROOT)
                                + "-" + document.getFolio() + "\"")
                .body(new FileSystemResource(fileStorage.resolve(stored)));
    }
}
