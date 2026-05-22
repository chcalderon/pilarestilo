package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PublicationExternalResultRequest(
        String workflowRunId,
        @NotNull Integer attemptNumber,
        @NotNull String status,
        String remotePostId,
        Instant publishedAt,
        String errorCode,
        String errorMessage
) {
}
