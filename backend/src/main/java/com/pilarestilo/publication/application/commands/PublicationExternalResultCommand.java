package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;

import java.time.Instant;

public record PublicationExternalResultCommand(
        String workflowRunId,
        int attemptNumber,
        PublicationAttemptStatus status,
        String remotePostId,
        Instant publishedAt,
        String errorCode,
        String errorMessage
) {
}
