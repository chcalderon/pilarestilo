package com.pilarestilo.productai.infrastructure.web.controllers;

import com.pilarestilo.productai.application.ProductAiService;
import com.pilarestilo.productai.application.dto.*;
import com.pilarestilo.productai.infrastructure.web.requests.ApproveProductAiDraftRequest;
import com.pilarestilo.productai.infrastructure.web.requests.CreateProductAiDraftRequest;
import com.pilarestilo.productai.infrastructure.web.requests.StartProductAiJobRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/product-ai")
@PreAuthorize("hasAnyRole('ADMIN','SELLER')")
public class ProductAiController {

    private final ProductAiService productAiService;

    public ProductAiController(ProductAiService productAiService) {
        this.productAiService = productAiService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<ProductAiDraftDto> createDraft(
            @Valid @RequestBody CreateProductAiDraftRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        ProductAiDraftDto dto = productAiService.createDraft(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/drafts/{draftId}/images")
    public ResponseEntity<ProductAiUploadResultDto> uploadImages(
            @PathVariable UUID draftId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String sourceFolder
    ) {
        ProductAiUploadResultDto dto = productAiService.uploadDraftImages(draftId, files, sourceFolder);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/jobs")
    public ResponseEntity<ProductAiJobDto> startJob(@Valid @RequestBody StartProductAiJobRequest request) {
        ProductAiJobDto dto = productAiService.startJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @PostMapping("/infer-single")
    public ProductAiInferenceDto inferSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String brandHint
    ) {
        return productAiService.inferSingleImage(file, brandHint);
    }

    @PostMapping("/transform-single")
    public ProductAiImageTransformDto transformSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String brandHint
    ) {
        return productAiService.transformSingleImage(file, provider, prompt, brandHint);
    }

    @GetMapping("/jobs")
    public List<ProductAiJobSummaryDto> listJobs() {
        return productAiService.listJobs();
    }

    @GetMapping("/jobs/{jobId}")
    public ProductAiJobDto getJob(@PathVariable UUID jobId) {
        return productAiService.getJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<ProductAiJobDto> retryJob(@PathVariable UUID jobId) {
        ProductAiJobDto dto = productAiService.retryJob(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @PostMapping("/drafts/{draftId}/approve-publish")
    public ProductAiPublishResultDto approveAndPublish(
            @PathVariable UUID draftId,
            @RequestBody(required = false) ApproveProductAiDraftRequest request
    ) {
        return productAiService.approveAndPublish(draftId, request);
    }
}
