package com.pilarestilo.publication.application.dto;

public record CreatePublicationResult(
        PublicationDto publication,
        boolean created
) {
}
