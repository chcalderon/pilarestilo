package com.pilarestilo.publication.infrastructure.web.controllers;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.PublicationExternalResultCommand;
import com.pilarestilo.publication.application.dto.PublicationExternalResultDto;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.infrastructure.n8n.N8nPublicationWebhookDispatcher;
import com.pilarestilo.publication.infrastructure.n8n.SocialPublishingN8nConfigResolver;
import com.pilarestilo.publication.infrastructure.web.requests.PublicationExternalResultRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/publications")
public class PublicationWebhookController {

    private final PublicationService publicationService;
    private final N8nPublicationWebhookDispatcher dispatcher;
    private final SocialPublishingN8nConfigResolver configResolver;

    public PublicationWebhookController(PublicationService publicationService,
                                        N8nPublicationWebhookDispatcher dispatcher,
                                        SocialPublishingN8nConfigResolver configResolver) {
        this.publicationService = publicationService;
        this.dispatcher = dispatcher;
        this.configResolver = configResolver;
    }

    @PostMapping("/{id}/external-result")
    public PublicationExternalResultDto registerExternalResult(@PathVariable UUID id,
                                                               @Valid @RequestBody PublicationExternalResultRequest request,
                                                               HttpServletRequest httpRequest) {
        String tokenHeaderName = configResolver.resolve().tokenHeaderName();
        String providedToken = httpRequest.getHeader(tokenHeaderName);
        if (!dispatcher.isValidCallbackToken(providedToken)) {
            throw new AccessDeniedException("Invalid social publishing callback token");
        }
        return publicationService.registerExternalResult(id, new PublicationExternalResultCommand(
                request.workflowRunId(),
                request.attemptNumber(),
                PublicationAttemptStatus.valueOf(request.status().trim().toUpperCase()),
                request.remotePostId(),
                request.publishedAt(),
                request.errorCode(),
                request.errorMessage()
        ));
    }
}
