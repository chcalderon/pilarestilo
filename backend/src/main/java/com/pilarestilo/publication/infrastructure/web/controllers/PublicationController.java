package com.pilarestilo.publication.infrastructure.web.controllers;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.application.usecases.GetProductPublicationImageHistoryUseCase;
import com.pilarestilo.publication.application.usecases.PublishProductsBatchUseCase;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.publication.infrastructure.web.requests.ApprovePublicationRequest;
import com.pilarestilo.publication.infrastructure.web.requests.CreatePublicationRequest;
import com.pilarestilo.publication.infrastructure.web.requests.PublishProductsBatchRequest;
import com.pilarestilo.publication.infrastructure.web.requests.RejectPublicationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/publications")
public class PublicationController {

    private final PublicationService publicationService;
    private final PublishProductsBatchUseCase publishProductsBatchUseCase;
    private final GetProductPublicationImageHistoryUseCase getProductPublicationImageHistoryUseCase;

    public PublicationController(PublicationService publicationService,
                                 PublishProductsBatchUseCase publishProductsBatchUseCase,
                                 GetProductPublicationImageHistoryUseCase getProductPublicationImageHistoryUseCase) {
        this.publicationService = publicationService;
        this.publishProductsBatchUseCase = publishProductsBatchUseCase;
        this.getProductPublicationImageHistoryUseCase = getProductPublicationImageHistoryUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public ResponseEntity<PublicationDto> create(@Valid @RequestBody CreatePublicationRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        CreatePublicationResult result = publicationService.create(toCommand(request), currentUser == null ? null : currentUser.id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.publication());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public List<PublicationDto> list() {
        return publicationService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public PublicationDto get(@PathVariable UUID id) {
        return publicationService.get(id);
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationDto submitForReview(@PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publicationService.submitForReview(id, currentUser == null ? null : currentUser.id());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationDto approve(@PathVariable UUID id,
                                  @RequestBody(required = false) ApprovePublicationRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publicationService.approve(id, currentUser == null ? null : currentUser.id(), request == null ? null : request.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationDto reject(@PathVariable UUID id,
                                 @RequestBody(required = false) RejectPublicationRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publicationService.reject(id, currentUser == null ? null : currentUser.id(), request == null ? null : request.comment());
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationDto dispatch(@PathVariable UUID id,
                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publicationService.dispatch(id, currentUser == null ? null : currentUser.id());
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationDto retry(@PathVariable UUID id,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publicationService.retry(id, currentUser == null ? null : currentUser.id());
    }

    @GetMapping("/products/{productId}/image-history")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public List<String> productImageHistory(@PathVariable UUID productId) {
        return getProductPublicationImageHistoryUseCase.execute(productId);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublishProductsBatchResult publishBatch(@Valid @RequestBody PublishProductsBatchRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publishProductsBatchUseCase.execute(toBatchCommand(request), currentUser == null ? null : currentUser.id());
    }

    private PublishProductsBatchCommand toBatchCommand(PublishProductsBatchRequest request) {
        Set<PublicationPlatform> platforms = request.platforms().stream()
                .map(p -> PublicationPlatform.valueOf(p.trim().toUpperCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> imageOverrides = request.imageOverrides() == null
                ? Map.of()
                : request.imageOverrides().entrySet().stream()
                        .collect(Collectors.toMap(e -> UUID.fromString(e.getKey()), Map.Entry::getValue));
        return new PublishProductsBatchCommand(
                request.productIds(),
                platforms,
                request.captionTemplate(),
                request.hashtags() == null ? List.of() : request.hashtags(),
                request.campaignLabel(),
                imageOverrides
        );
    }

    private CreatePublicationCommand toCommand(CreatePublicationRequest request) {
        return new CreatePublicationCommand(
                request.productId(),
                PublicationSourceType.valueOf(request.sourceType().trim().toUpperCase()),
                request.sourceId(),
                PublicationPlatform.valueOf(request.platform().trim().toUpperCase()),
                PublicationChannelType.valueOf(request.channelType().trim().toUpperCase()),
                request.locale(),
                request.campaignLabel(),
                request.caption(),
                request.hashtags(),
                request.approvalRequired() == null || request.approvalRequired(),
                request.scheduledAt(),
                request.idempotencyKey(),
                request.mediaBundles() == null ? List.of() : request.mediaBundles().stream()
                        .map(bundle -> new CreatePublicationCommand.MediaBundleCommand(
                                PublicationMediaBundleType.valueOf(bundle.bundleType().trim().toUpperCase()),
                                bundle.primaryAssetUrl(),
                                bundle.assetManifest()
                        ))
                        .toList()
        );
    }
}
