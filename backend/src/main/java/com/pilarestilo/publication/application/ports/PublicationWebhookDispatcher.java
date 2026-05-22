package com.pilarestilo.publication.application.ports;

import com.pilarestilo.publication.application.dto.PublicationDispatchWebhookPayload;

import java.util.UUID;

public interface PublicationWebhookDispatcher {
    DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchWebhookPayload payload);

    record DispatchResult(String requestId, String payloadHash) {
    }
}
