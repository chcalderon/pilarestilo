package com.pilarestilo.publication.application;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.dto.CampaignDetailDto;
import com.pilarestilo.publication.application.dto.CampaignSummaryDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CampaignReportService {

    private final PublicationBatchJpaRepository batchRepository;
    private final PublicationJpaRepository publicationRepository;
    private final PublicationMetricsJpaRepository metricsRepository;
    private final ProductRepository productRepository;

    public CampaignReportService(PublicationBatchJpaRepository batchRepository,
                                 PublicationJpaRepository publicationRepository,
                                 PublicationMetricsJpaRepository metricsRepository,
                                 ProductRepository productRepository) {
        this.batchRepository = batchRepository;
        this.publicationRepository = publicationRepository;
        this.metricsRepository = metricsRepository;
        this.productRepository = productRepository;
    }

    public List<CampaignSummaryDto> listCampaigns() {
        Map<String, List<PublicationEntity>> byLabel = groupPublicationsByLabel();
        Map<UUID, PublicationMetricsEntity> metrics = loadMetrics(byLabel);
        Map<String, Integer> batchCounts = batchCountsByLabel();

        List<CampaignSummaryDto> out = new ArrayList<>();
        for (Map.Entry<String, List<PublicationEntity>> e : byLabel.entrySet()) {
            List<PublicationEntity> rows = e.getValue();
            EnumSet<PublicationPlatform> platforms = EnumSet.noneOf(PublicationPlatform.class);
            int published = 0;
            int failed = 0;
            int scheduled = 0;
            int postsWithError = 0;
            long tImp = 0;
            long tReach = 0;
            long tLikes = 0;
            long tComments = 0;
            long tShares = 0;
            long tSaved = 0;
            Instant first = null;
            Instant last = null;
            for (PublicationEntity r : rows) {
                platforms.add(r.getPlatform());
                switch (r.getStatus()) {
                    case PUBLISHED -> published++;
                    case FAILED -> failed++;
                    case SCHEDULED -> scheduled++;
                    default -> { /* RETRY_SCHEDULED, APPROVED, DRAFT, PUBLISHING: counted only in totalPosts */ }
                }
                if (first == null || r.getCreatedAt().isBefore(first)) {
                    first = r.getCreatedAt();
                }
                if (last == null || r.getCreatedAt().isAfter(last)) {
                    last = r.getCreatedAt();
                }
                PublicationMetricsEntity m = metrics.get(r.getId());
                if (m != null) {
                    if (m.getFetchError() != null) {
                        postsWithError++;
                    }
                    tImp += nz(m.getImpressions());
                    tReach += nz(m.getReach());
                    tLikes += nz(m.getLikes());
                    tComments += nz(m.getComments());
                    tShares += nz(m.getShares());
                    tSaved += nz(m.getSaved());
                }
            }
            out.add(new CampaignSummaryDto(
                    e.getKey(), first, last, batchCounts.getOrDefault(e.getKey(), 0), rows.size(),
                    published, failed, scheduled, platforms,
                    new CampaignSummaryDto.MetricsTotals(tImp, tReach, tLikes, tComments, tShares, tSaved),
                    postsWithError));
        }
        out.sort(Comparator.comparing(CampaignSummaryDto::lastPostAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public CampaignDetailDto getCampaign(String label) {
        List<PublicationEntity> rows = groupPublicationsByLabel().getOrDefault(label, List.of());
        if (rows.isEmpty()) {
            return new CampaignDetailDto(label, null, null, List.of());
        }
        Map<UUID, PublicationMetricsEntity> metrics = metricsRepository
                .findByPublicationIdIn(rows.stream().map(PublicationEntity::getId).toList()).stream()
                .collect(Collectors.toMap(PublicationMetricsEntity::getPublicationId, m -> m));
        Map<UUID, Product> products = productRepository.findAllByIds(rows.stream()
                        .map(PublicationEntity::getProductId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<CampaignDetailDto.PostRow> postRows = new ArrayList<>();
        Instant first = null;
        Instant last = null;
        for (PublicationEntity r : rows) {
            if (first == null || r.getCreatedAt().isBefore(first)) {
                first = r.getCreatedAt();
            }
            if (last == null || r.getCreatedAt().isAfter(last)) {
                last = r.getCreatedAt();
            }
            Product p = r.getProductId() == null ? null : products.get(r.getProductId());
            PublicationMetricsEntity m = metrics.get(r.getId());
            postRows.add(new CampaignDetailDto.PostRow(
                    r.getId(), r.getProductId(),
                    p != null ? p.getName() : "(producto eliminado)",
                    p != null ? p.getImageUrl() : null,
                    r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                    m == null ? null : toPostMetrics(m),
                    m == null ? null : m.getFetchError(),
                    m == null ? null : m.getFetchedAt()));
        }
        return new CampaignDetailDto(label, first, last, postRows);
    }

    private Map<String, List<PublicationEntity>> groupPublicationsByLabel() {
        List<PublicationBatchEntity> batches = batchRepository.findAll().stream()
                .filter(b -> b.getCampaignLabel() != null && !b.getCampaignLabel().isBlank())
                .toList();
        Map<UUID, String> labelByBatch = batches.stream()
                .collect(Collectors.toMap(PublicationBatchEntity::getId, PublicationBatchEntity::getCampaignLabel));
        List<PublicationEntity> pubs = labelByBatch.isEmpty() ? List.of()
                : publicationRepository.findByBatchIdInOrderByCreatedAtAsc(labelByBatch.keySet());
        Map<String, List<PublicationEntity>> byLabel = new LinkedHashMap<>();
        for (PublicationEntity p : pubs) {
            String label = labelByBatch.get(p.getBatchId());
            if (label != null) {
                byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(p);
            }
        }
        return byLabel;
    }

    private Map<String, Integer> batchCountsByLabel() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PublicationBatchEntity b : batchRepository.findAll()) {
            if (b.getCampaignLabel() != null && !b.getCampaignLabel().isBlank()) {
                counts.merge(b.getCampaignLabel(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<UUID, PublicationMetricsEntity> loadMetrics(Map<String, List<PublicationEntity>> byLabel) {
        Set<UUID> ids = byLabel.values().stream().flatMap(List::stream)
                .map(PublicationEntity::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return metricsRepository.findByPublicationIdIn(ids).stream()
                .collect(Collectors.toMap(PublicationMetricsEntity::getPublicationId, m -> m));
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static PostMetrics toPostMetrics(PublicationMetricsEntity m) {
        return new PostMetrics(m.getImpressions(), m.getReach(), m.getLikes(),
                m.getComments(), m.getShares(), m.getSaved());
    }
}
