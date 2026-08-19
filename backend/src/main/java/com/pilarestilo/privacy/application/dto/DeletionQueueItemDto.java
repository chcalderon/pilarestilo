package com.pilarestilo.privacy.application.dto;

import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.user.domain.model.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A deletion request as the shop's desk needs to read it: with the person attached.
 *
 * <p>The queue is answered by writing to somebody, so a row showing only a UUID cannot be acted on
 * — which is the complaint the payments screen already earned. The name is read live rather than
 * copied, so once the request is carried out the row shows the anonymised account: the proof that
 * the work was done is the same field that identified them before.
 *
 * @param daysWaiting how long the person has been waiting for an answer, frozen once answered
 */
public record DeletionQueueItemDto(
        UUID id,
        UUID userId,
        String customerName,
        String customerEmail,
        String status,
        String reason,
        Instant requestedAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolution,
        long daysWaiting
) {
    public static DeletionQueueItemDto from(DataDeletionRequest request, Optional<User> user) {
        Instant until = request.getResolvedAt() != null ? request.getResolvedAt() : Instant.now();
        return new DeletionQueueItemDto(
                request.getId(),
                request.getUserId(),
                user.map(User::getFullName).orElse(null),
                user.map(User::getEmail).orElse(null),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedAt(),
                request.getResolvedAt(),
                request.getResolvedBy(),
                request.getResolution(),
                Duration.between(request.getRequestedAt(), until).toDays());
    }
}
