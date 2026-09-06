package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class PublicationMetricsJpaRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PublicationMetricsJpaRepository repo;
    @Autowired PublicationJpaRepository publicationRepo;

    private UUID savePublication() {
        PublicationEntity p = new PublicationEntity();
        p.setId(UUID.randomUUID());
        p.setSourceType(PublicationSourceType.PRODUCT);
        p.setPlatform(PublicationPlatform.INSTAGRAM);
        p.setChannelType(PublicationChannelType.FEED_POST);
        p.setStatus(PublicationStatus.PUBLISHED);
        p.setApprovalStatus(PublicationApprovalStatus.APPROVED);
        p.setLocale("es-CL");
        p.setIdempotencyKey("metrics-it-" + UUID.randomUUID());
        p.setContentVersion(1);
        p.setSnapshotVersion(0);
        p.setRetryCount(0);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return publicationRepo.save(p).getId();
    }

    @Test
    void saves_and_finds_by_publication_id_in() {
        UUID pubId = savePublication();
        PublicationMetricsEntity e = new PublicationMetricsEntity();
        e.setPublicationId(pubId);
        e.setLikes(42L);
        e.setImpressions(null);
        e.setFetchedAt(Instant.now());
        repo.save(e);

        List<PublicationMetricsEntity> found = repo.findByPublicationIdIn(List.of(pubId, UUID.randomUUID()));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getLikes()).isEqualTo(42L);
        assertThat(found.get(0).getImpressions()).isNull();
    }
}
