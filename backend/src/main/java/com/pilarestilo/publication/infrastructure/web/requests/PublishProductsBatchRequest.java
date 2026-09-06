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
        /** Per-product ordered image list, keyed by productId (string keys — JSON objects can't
         *  key by UUID). */
        Map<String, List<String>> imageSelections,
        /** Per-product chosen variant, keyed by productId (as a string, same reason as above). */
        Map<String, VariantSelectionRequest> variantSelections,
        /** ISO-8601 instant. When set, the batch is scheduled instead of published now. */
        String scheduledAt
) {
    public record VariantSelectionRequest(String color, String size) {}
}
