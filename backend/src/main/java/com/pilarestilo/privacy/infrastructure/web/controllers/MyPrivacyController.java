package com.pilarestilo.privacy.infrastructure.web.controllers;

import com.pilarestilo.privacy.application.dto.PersonalDataExportDto;
import com.pilarestilo.privacy.application.usecases.ExportMyDataUseCase;
import com.pilarestilo.privacy.application.dto.DataDeletionRequestDto;
import com.pilarestilo.privacy.application.usecases.RecordConsentUseCase;
import com.pilarestilo.privacy.application.usecases.RequestDataDeletionUseCase;
import com.pilarestilo.privacy.infrastructure.web.requests.RequestDeletionRequest;
import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's own rights over her data: what the shop holds, and what she agreed to.
 *
 * <p>Under {@code /api/me} rather than {@code /api/admin}: these are hers to exercise without
 * asking anybody, which is the point of the Ley 21.719.
 */
@RestController
@RequestMapping("/api/me/privacy")
public class MyPrivacyController {

    private final ExportMyDataUseCase exportMyDataUseCase;
    private final RecordConsentUseCase recordConsentUseCase;
    private final RequestDataDeletionUseCase requestDataDeletionUseCase;

    public MyPrivacyController(ExportMyDataUseCase exportMyDataUseCase,
                               RecordConsentUseCase recordConsentUseCase,
                               RequestDataDeletionUseCase requestDataDeletionUseCase) {
        this.exportMyDataUseCase = exportMyDataUseCase;
        this.recordConsentUseCase = recordConsentUseCase;
        this.requestDataDeletionUseCase = requestDataDeletionUseCase;
    }

    /** Everything held about her, as a file she can keep. */
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PersonalDataExportDto> export(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mis-datos.json\"")
                .body(exportMyDataUseCase.execute(currentUser.id()));
    }

    /** Opting in to marketing, which is separate from the terms and freely given. */
    @PostMapping("/marketing")
    @PreAuthorize("isAuthenticated()")
    public void acceptMarketing(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                HttpServletRequest request) {
        recordConsentUseCase.execute(
                currentUser.id(), ConsentType.MARKETING,
                callerAddress(request), request.getHeader("User-Agent"));
    }

    /** And taking it back, which has to be as easy as giving it. */
    @PostMapping("/marketing/revoke")
    @PreAuthorize("isAuthenticated()")
    public void revokeMarketing(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        recordConsentUseCase.revokeMarketing(currentUser.id());
    }

    /**
     * Asking to be forgotten. It queues rather than acting: an order in flight has to arrive first,
     * and the shop has to be able to say when it was asked and what it did about it.
     */
    @PostMapping("/deletion")
    @PreAuthorize("isAuthenticated()")
    public DataDeletionRequestDto requestDeletion(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                  @Valid @RequestBody(required = false) RequestDeletionRequest request) {
        return DataDeletionRequestDto.from(requestDataDeletionUseCase.execute(
                currentUser.id(), request == null ? null : request.reason()));
    }

    private static String callerAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
