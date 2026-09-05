package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import com.pilarestilo.product.domain.model.ProductVariant;
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates a multi-product, multi-platform publish. Deliberately not @Transactional: each
 * item's create()+dispatch() opens its own transaction on PublicationService (a different Spring
 * bean, so its @Transactional proxy applies independently). Wrapping this loop in one outer
 * transaction would mark it rollback-only the moment any single item's call threw, losing every
 * other item's already-recorded result too — the opposite of "each row is independent."
 */
@Component
public class PublishProductsBatchUseCase {

    private final PublicationService publicationService;
    private final ProductRepository productRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final ObjectMapper objectMapper;

    public PublishProductsBatchUseCase(PublicationService publicationService,
                                       ProductRepository productRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository,
                                       ObjectMapper objectMapper) {
        this.publicationService = publicationService;
        this.productRepository = productRepository;
        this.publicationBatchRepository = publicationBatchRepository;
        this.objectMapper = objectMapper;
    }

    public PublishProductsBatchResult execute(PublishProductsBatchCommand command, UUID actorUserId) {
        List<PublishProductsBatchResult.PublicationItemResult> items = new ArrayList<>();

        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(trimToNull(command.campaignLabel()));
        batch.setCreatedBy(actorUserId);
        batch.setCreatedAt(Instant.now());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                for (PublicationPlatform platform : command.platforms()) {
                    items.add(new PublishProductsBatchResult.PublicationItemResult(
                            productId, platform, false, null, "Producto no encontrado: " + productId, false));
                }
                continue;
            }
            String caption = interpolate(command.captionTemplate(), product, command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                items.add(publishOne(productId, product, platform, caption, command, actorUserId, batch.getId()));
            }
        }

        return new PublishProductsBatchResult(items);
    }

    private String serializeHashtags(List<String> hashtags) {
        List<String> clean = hashtags == null ? List.of()
                : hashtags.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PublishProductsBatchResult.PublicationItemResult publishOne(UUID productId,
                                                                        Product product,
                                                                        PublicationPlatform platform,
                                                                        String caption,
                                                                        PublishProductsBatchCommand command,
                                                                        UUID actorUserId,
                                                                        UUID batchId) {
        try {
            CreatePublicationCommand createCommand = new CreatePublicationCommand(
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
                    null,
                    "pub-batch-" + productId + "-" + platform.name() + "-" + UUID.randomUUID(),
                    List.of(new CreatePublicationCommand.MediaBundleCommand(
                            PublicationMediaBundleType.SOCIAL_FEED,
                            command.imageOverrides().getOrDefault(productId, product.getImageUrl()),
                            Map.of()
                    )),
                    batchId
            );
            CreatePublicationResult created = publicationService.create(createCommand, actorUserId);
            PublicationDto dispatched = publicationService.dispatch(created.publication().id(), actorUserId);

            boolean success = dispatched.status() == PublicationStatus.PUBLISHED;
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, success, dispatched.id(),
                    success ? null : dispatched.lastErrorMessage(), false);
        } catch (DomainException ex) {
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, false, null, ex.getMessage(), false);
        }
    }

    private String interpolate(String template, Product product, PublishProductsBatchCommand.VariantSelection selection) {
        String priceText = NumberFormat.getInstance(Locale.of("es", "CL")).format(product.getPrice().amount());
        ProductVariant variant = selection == null ? null : resolveVariant(product, selection);
        return template
                .replace("{producto}", product.getName())
                .replace("{precio}", "$" + priceText)
                .replace("{color}", variant == null ? "" : variant.getColor())
                .replace("{talla}", variant == null ? "" : variant.getSize())
                .replace("{cantidad}", variant == null ? "" : String.valueOf(variant.available()));
    }

    private ProductVariant resolveVariant(Product product, PublishProductsBatchCommand.VariantSelection selection) {
        return product.getVariants().stream()
                .filter(v -> Objects.equals(v.getColor(), selection.color()) && Objects.equals(v.getSize(), selection.size()))
                .findFirst()
                .orElse(null);
    }
}
