package com.pilarestilo.publication.application;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.dto.CampaignSummaryDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignReportServiceTest {

    @Mock PublicationBatchJpaRepository batchRepo;
    @Mock PublicationJpaRepository publicationRepo;
    @Mock PublicationMetricsJpaRepository metricsRepo;
    @Mock ProductRepository productRepo;

    private PublicationBatchEntity batch(UUID id, String label) {
        PublicationBatchEntity b = new PublicationBatchEntity();
        b.setId(id);
        b.setCampaignLabel(label);
        b.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return b;
    }

    private PublicationEntity pub(UUID batchId, UUID productId, PublicationPlatform platform, PublicationStatus status) {
        PublicationEntity p = new PublicationEntity();
        p.setId(UUID.randomUUID());
        p.setBatchId(batchId);
        p.setProductId(productId);
        p.setPlatform(platform);
        p.setStatus(status);
        p.setCreatedAt(Instant.parse("2026-09-02T00:00:00Z"));
        return p;
    }

    @Test
    void groups_batches_by_label_and_sums_metrics_treating_null_as_zero() {
        UUID b1 = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        var pub1 = pub(b1, productId, PublicationPlatform.INSTAGRAM, PublicationStatus.PUBLISHED);
        var pub2 = pub(b1, productId, PublicationPlatform.FACEBOOK, PublicationStatus.FAILED);
        when(batchRepo.findAll()).thenReturn(List.of(batch(b1, "Verano")));
        when(publicationRepo.findByBatchIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(pub1, pub2));

        PublicationMetricsEntity m = new PublicationMetricsEntity();
        m.setPublicationId(pub1.getId());
        m.setLikes(10L);
        m.setImpressions(null);
        m.setFetchError(null);
        m.setFetchedAt(Instant.now());
        when(metricsRepo.findByPublicationIdIn(any())).thenReturn(List.of(m));

        List<CampaignSummaryDto> out = new CampaignReportService(batchRepo, publicationRepo, metricsRepo, productRepo)
                .listCampaigns();

        assertEquals(1, out.size());
        assertEquals("Verano", out.get(0).label());
        assertEquals(2, out.get(0).totalPosts());
        assertEquals(1, out.get(0).published());
        assertEquals(1, out.get(0).failed());
        assertEquals(10L, out.get(0).totals().likes());
        assertEquals(0L, out.get(0).totals().impressions());
    }

    @Test
    void unknown_label_yields_an_empty_detail() {
        when(batchRepo.findAll()).thenReturn(List.of());
        var detail = new CampaignReportService(batchRepo, publicationRepo, metricsRepo, productRepo)
                .getCampaign("nope");
        assertEquals("nope", detail.label());
        assertEquals(0, detail.posts().size());
    }
}
