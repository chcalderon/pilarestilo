package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublishProductsBatchRequest(
        @NotEmpty List<UUID> productIds,
        @NotEmpty List<@NotBlank String> platforms,
        @NotBlank String captionTemplate,
        List<String> hashtags,
        String campaignLabel,
        /** Per-product replacement image URL, keyed by productId (as a string — JSON object keys
         *  can't be UUIDs directly). */
        Map<String, String> imageOverrides,
        /** Per-product chosen variant, keyed by productId (as a string, same reason as above). */
        Map<String, VariantSelectionRequest> variantSelections,
        /** ISO-8601 instant. When set, the batch is scheduled instead of published now. */
        String scheduledAt
) {
    public record VariantSelectionRequest(String color, String size) {}
}
