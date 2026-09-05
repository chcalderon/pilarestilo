package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the per-product×platform {@link CreatePublicationCommand}s for a publish batch: caption
 * interpolation, variant resolution, media bundle, idempotency-key scheme and hashtag
 * serialization. Shared by {@link PublishProductsBatchUseCase} and
 * {@link UpdateScheduledBatchUseCase} so those rules live in one place.
 */
@Component
class BatchPublicationFactory {

    private final ObjectMapper objectMapper;

    BatchPublicationFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String serializeHashtags(List<String> hashtags) {
        List<String> clean = hashtags == null ? List.of()
                : hashtags.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    String interpolate(String template, Product product, PublishProductsBatchCommand.VariantSelection selection) {
        String priceText = NumberFormat.getInstance(Locale.of("es", "CL")).format(product.getPrice().amount());
        ProductVariant variant = selection == null ? null : resolveVariant(product, selection);
        return template
                .replace("{producto}", product.getName())
                .replace("{precio}", "$" + priceText)
                .replace("{color}", variant == null ? "" : variant.getColor())
                .replace("{talla}", variant == null ? "" : variant.getSize())
                .replace("{cantidad}", variant == null ? "" : String.valueOf(variant.available()));
    }

    CreatePublicationCommand buildCreateCommand(PublishProductsBatchCommand command, UUID productId, Product product,
                                                PublicationPlatform platform, String caption, UUID batchId) {
        return new CreatePublicationCommand(
                productId,
                PublicationSourceType.PRODUCT,
                productId,
                platform,
                PublicationChannelType.FEED_POST,
                "es-CL",
                command.campaignLabel(),
                caption,
                command.hashtags(),
                false,
                command.scheduledAt(),
                "pub-batch-" + productId + "-" + platform.name() + "-" + UUID.randomUUID(),
                List.of(new CreatePublicationCommand.MediaBundleCommand(
                        PublicationMediaBundleType.SOCIAL_FEED,
                        command.imageOverrides().getOrDefault(productId, product.getImageUrl()),
                        Map.of()
                )),
                batchId
        );
    }

    private ProductVariant resolveVariant(Product product, PublishProductsBatchCommand.VariantSelection selection) {
        return product.getVariants().stream()
                .filter(v -> Objects.equals(v.getColor(), selection.color()) && Objects.equals(v.getSize(), selection.size()))
                .findFirst()
                .orElse(null);
    }
}
