package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PublishProductsBatchCommand(
        List<UUID> productIds,
        Set<PublicationPlatform> platforms,
        String captionTemplate,
        List<String> hashtags,
        String campaignLabel,
        /** Per-product ordered image list for this post: cover/override first, then any extra
         *  carousel images. A product missing from this map posts a single image, its catalog
         *  photo as-is. */
        Map<UUID, List<String>> imageSelections,
        /** Per-product chosen variant, keyed by productId, used to resolve the {color}/{talla}/
         *  {cantidad} caption tokens. A product missing from this map (or with no matching
         *  variant) resolves those tokens to an empty string. */
        Map<UUID, VariantSelection> variantSelections,
        /** When set, the batch is created as SCHEDULED and a background job publishes it at this
         *  instant instead of publishing immediately. */
        Instant scheduledAt
) {
    public record VariantSelection(String color, String size) {}
}
