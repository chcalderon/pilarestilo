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
            out.add(summarize(e.getKey(), e.getValue(), metrics, batchCounts.getOrDefault(e.getKey(), 0)));
        }
        out.sort(Comparator.comparing(CampaignSummaryDto::lastPostAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private CampaignSummaryDto summarize(String label, List<PublicationEntity> rows,
                                         Map<UUID, PublicationMetricsEntity> metrics, int batchCount) {
        EnumSet<PublicationPlatform> platforms = EnumSet.noneOf(PublicationPlatform.class);
        int published = 0;
        int failed = 0;
        int scheduled = 0;
        int postsWithError = 0;
        Totals t = new Totals();
        TimeRange range = new TimeRange();
        for (PublicationEntity r : rows) {
            platforms.add(r.getPlatform());
            switch (r.getStatus()) {
                case PUBLISHED -> published++;
                case FAILED -> failed++;
                case SCHEDULED -> scheduled++;
                default -> { /* RETRY_SCHEDULED, APPROVED, DRAFT, PUBLISHING: counted only in totalPosts */ }
            }
            range.include(r.getCreatedAt());
            PublicationMetricsEntity m = metrics.get(r.getId());
            if (m != null) {
                if (m.getFetchError() != null) {
                    postsWithError++;
                }
                t.add(m);
            }
        }
        return new CampaignSummaryDto(label, range.first, range.last, batchCount, rows.size(),
                published, failed, scheduled, platforms, t.toDto(), postsWithError);
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

        TimeRange range = new TimeRange();
        List<CampaignDetailDto.PostRow> postRows = new ArrayList<>();
        for (PublicationEntity r : rows) {
            range.include(r.getCreatedAt());
            postRows.add(toPostRow(r, products.get(r.getProductId()), metrics.get(r.getId())));
        }
        return new CampaignDetailDto(label, range.first, range.last, postRows);
    }

    private CampaignDetailDto.PostRow toPostRow(PublicationEntity r, Product p, PublicationMetricsEntity m) {
        return new CampaignDetailDto.PostRow(
                r.getId(), r.getProductId(),
                p != null ? p.getName() : "(producto eliminado)",
                p != null ? p.getImageUrl() : null,
                r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                m == null ? null : toPostMetrics(m),
                m == null ? null : m.getFetchError(),
                m == null ? null : m.getFetchedAt());
    }

    private static final class TimeRange {
        private Instant first;
        private Instant last;

        void include(Instant at) {
            if (at == null) {
                return;
            }
            if (first == null || at.isBefore(first)) {
                first = at;
            }
            if (last == null || at.isAfter(last)) {
                last = at;
            }
        }
    }

    private static final class Totals {
        private long imp;
        private long reach;
        private long likes;
        private long comments;
        private long shares;
        private long saved;

        void add(PublicationMetricsEntity m) {
            imp += nz(m.getImpressions());
            reach += nz(m.getReach());
            likes += nz(m.getLikes());
            comments += nz(m.getComments());
            shares += nz(m.getShares());
            saved += nz(m.getSaved());
        }

        CampaignSummaryDto.MetricsTotals toDto() {
            return new CampaignSummaryDto.MetricsTotals(imp, reach, likes, comments, shares, saved);
        }

        private static long nz(Long v) {
            return v == null ? 0L : v;
        }
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

    private static PostMetrics toPostMetrics(PublicationMetricsEntity m) {
        return new PostMetrics(m.getImpressions(), m.getReach(), m.getLikes(),
                m.getComments(), m.getShares(), m.getSaved());
    }
}
