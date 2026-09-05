package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

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
        /** Per-product replacement image URL (e.g. an edited version uploaded just for this post),
         *  keyed by productId. A product missing from this map uses its catalog photo as-is. */
        Map<UUID, String> imageOverrides,
        /** Per-product chosen variant, keyed by productId, used to resolve the {color}/{talla}/
         *  {cantidad} caption tokens. A product missing from this map (or with no matching
         *  variant) resolves those tokens to an empty string. */
        Map<UUID, VariantSelection> variantSelections
) {
    public record VariantSelection(String color, String size) {}
}
