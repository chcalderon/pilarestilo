package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationAttemptTriggerType;

import java.time.Instant;
import java.util.UUID;

public record PublicationAttemptDto(
        UUID id,
        int attemptNumber,
        PublicationAttemptTriggerType triggerType,
        String requestId,
        String workflowRunId,
        PublicationAttemptStatus status,
        String remoteStatus,
        String remotePostId,
        String errorCode,
        String errorMessage,
        String payloadHash,
        Instant startedAt,
        Instant finishedAt
) {
}
