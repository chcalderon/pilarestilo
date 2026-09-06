package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Every image ever used to publish a given product, most recent first — sourced straight from
 * publication_media_bundles (no new table, no upload is ever lost as long as it made it into a
 * real publish attempt). Deduplicated: reusing the same photo across several posts only lists it
 * once.
 */
@Component
public class GetProductPublicationImageHistoryUseCase {

    private static final int MAX_RESULTS = 12;

    private final PublicationJpaRepository publicationRepository;

    public GetProductPublicationImageHistoryUseCase(PublicationJpaRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    public List<String> execute(UUID productId) {
        return publicationRepository.findTop20ByProductIdOrderByCreatedAtDesc(productId).stream()
                .flatMap(publication -> publication.getMediaBundles().stream())
                .map(PublicationMediaBundleEntity::getPrimaryAssetUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .limit(MAX_RESULTS)
                .toList();
    }
}
