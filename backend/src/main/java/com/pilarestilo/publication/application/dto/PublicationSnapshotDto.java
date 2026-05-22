package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationSnapshotType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublicationSnapshotDto(
        UUID id,
        PublicationSnapshotType snapshotType,
        Map<String, Object> payload,
        int version,
        Instant createdAt
) {
}
