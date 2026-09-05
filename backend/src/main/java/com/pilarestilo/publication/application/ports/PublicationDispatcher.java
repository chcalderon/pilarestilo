package com.pilarestilo.publication.application.ports;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;

import java.util.UUID;

public interface PublicationDispatcher {
    DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload);

    record DispatchResult(
            String requestId,
            String payloadHash,
            PublicationAttemptStatus status,
            String remotePostId,
            String errorCode,
            String errorMessage
    ) {}
}
