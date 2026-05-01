package com.pilarestilo.productai.application;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.productai.application.dto.*;
import com.pilarestilo.productai.domain.enums.ProductAiDraftStatus;
import com.pilarestilo.productai.domain.enums.ProductAiJobStatus;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiAssetEntity;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiDraftEntity;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiJobEntity;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiOutputEntity;
import com.pilarestilo.productai.infrastructure.ai.ProductAiNodeBridgeClient;
import com.pilarestilo.productai.infrastructure.ai.ProductAiOllamaClient;
import com.pilarestilo.productai.infrastructure.persistence.repositories.ProductAiAssetJpaRepository;
import com.pilarestilo.productai.infrastructure.persistence.repositories.ProductAiDraftJpaRepository;
import com.pilarestilo.productai.infrastructure.persistence.repositories.ProductAiJobJpaRepository;
import com.pilarestilo.productai.infrastructure.persistence.repositories.ProductAiOutputJpaRepository;
import com.pilarestilo.productai.infrastructure.web.requests.ApproveProductAiDraftRequest;
import com.pilarestilo.productai.infrastructure.web.requests.CreateProductAiDraftRequest;
import com.pilarestilo.productai.infrastructure.web.requests.StartProductAiJobRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProductAiService {

    private static final Logger log = LoggerFactory.getLogger(ProductAiService.class);

    private final ProductAiDraftJpaRepository draftRepository;
    private final ProductAiAssetJpaRepository assetRepository;
    private final ProductAiJobJpaRepository jobRepository;
    private final ProductAiOutputJpaRepository outputRepository;
    private final MediaStorageService mediaStorageService;
    private final CreateProductUseCase createProductUseCase;
    private final ProductAiNodeBridgeClient nodeBridgeClient;
    private final ProductAiOllamaClient ollamaClient;

    @Value("${app.product-ai.max-attempts:3}")
    private int defaultMaxAttempts;

    @Value("${app.product-ai.retry-backoff-ms:2000}")
    private long retryBackoffMs;

    @Value("${app.product-ai.worker.batch-size:5}")
    private int workerBatchSize;

    @Value("${app.product-ai.engine:stub}")
    private String productAiEngine;

    private final AtomicReference<String> lastReadinessReason = new AtomicReference<>(null);
    private volatile Instant lastReadinessLogAt;

    public ProductAiService(ProductAiDraftJpaRepository draftRepository,
                            ProductAiAssetJpaRepository assetRepository,
                            ProductAiJobJpaRepository jobRepository,
                            ProductAiOutputJpaRepository outputRepository,
                            MediaStorageService mediaStorageService,
                            CreateProductUseCase createProductUseCase,
                            ProductAiNodeBridgeClient nodeBridgeClient,
                            ProductAiOllamaClient ollamaClient) {
        this.draftRepository = draftRepository;
        this.assetRepository = assetRepository;
        this.jobRepository = jobRepository;
        this.outputRepository = outputRepository;
        this.mediaStorageService = mediaStorageService;
        this.createProductUseCase = createProductUseCase;
        this.nodeBridgeClient = nodeBridgeClient;
        this.ollamaClient = ollamaClient;
    }

    @Transactional
    public ProductAiDraftDto createDraft(CreateProductAiDraftRequest request, AuthenticatedUser currentUser) {
        ProductAiDraftEntity draft = new ProductAiDraftEntity();
        draft.setId(UUID.randomUUID());
        draft.setStatus(ProductAiDraftStatus.DRAFT);
        draft.setName(request.normalizedName());
        draft.setBrand(request.normalizedBrand());
        draft.setCondition(request.normalizedCondition());
        draft.setPriceAmount(request.priceAmount() == null ? new BigDecimal("10000") : request.priceAmount());
        draft.setPriceCurrency(request.normalizedCurrency());
        draft.setCreatedBy(currentUser.id());
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(draft.getCreatedAt());

        draftRepository.save(draft);
        return new ProductAiDraftDto(draft.getId(), draft.getProductId(), draft.getStatus().name(), draft.getCreatedAt());
    }

    @Transactional
    public ProductAiUploadResultDto uploadDraftImages(UUID draftId, List<MultipartFile> files, String sourceFolder) {
        ProductAiDraftEntity draft = requireDraft(draftId);
        if (files == null || files.isEmpty()) {
            throw new DomainException("At least one file is required");
        }

        String folder = "products/ai/original/" + draft.getId();
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            folder = folder + "/" + sanitizeFolderSegment(sourceFolder);
        }

        int baseIndex = assetRepository.findByDraftIdOrderBySortOrderAscCreatedAtAsc(draftId).size();
        List<ProductAiUploadedAssetDto> uploaded = new ArrayList<>();
        int offset = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String extension = mediaStorageService.resolveExtension(file);
            if (!Set.of("jpg", "png", "webp").contains(extension)) {
                throw new DomainException("Formato no soportado para pipeline IA: " + extension + ". Usa jpg, png o webp.");
            }
            String url = mediaStorageService.store(file, folder);
            ProductAiAssetEntity asset = new ProductAiAssetEntity();
            asset.setId(UUID.randomUUID());
            asset.setDraftId(draftId);
            asset.setOriginalUrl(url);
            asset.setSourceFilename(file.getOriginalFilename());
            asset.setSortOrder(baseIndex + offset);
            asset.setCreatedAt(Instant.now());
            assetRepository.save(asset);
            uploaded.add(new ProductAiUploadedAssetDto(asset.getId(), asset.getOriginalUrl(), asset.getSourceFilename()));
            offset++;
        }

        if (uploaded.isEmpty()) {
            throw new DomainException("No valid files uploaded");
        }

        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
        return new ProductAiUploadResultDto(draftId, uploaded);
    }

    @Transactional
    public ProductAiJobDto startJob(StartProductAiJobRequest request) {
        ProductAiDraftEntity draft = requireDraft(request.draftId());
        List<ProductAiAssetEntity> assets = assetRepository.findByDraftIdOrderBySortOrderAscCreatedAtAsc(request.draftId());
        if (assets.isEmpty()) {
            throw new DomainException("Draft has no images. Upload images first.");
        }

        boolean hasRunning = jobRepository.existsByDraftIdAndStatusIn(
                request.draftId(),
                List.of(ProductAiJobStatus.PENDING, ProductAiJobStatus.PROCESSING)
        );
        if (hasRunning) {
            throw new DomainException("Draft already has a pending/processing job");
        }

        ProductAiJobEntity job = new ProductAiJobEntity();
        job.setId(UUID.randomUUID());
        job.setDraftId(request.draftId());
        job.setStatus(ProductAiJobStatus.PENDING);
        job.setProgress(0);
        job.setAttempt(0);
        job.setMaxAttempts(Math.max(1, defaultMaxAttempts));
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(job.getCreatedAt());
        jobRepository.save(job);

        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);

        return toJobDto(job, List.of());
    }

    public ProductAiInferenceDto inferSingleImage(MultipartFile file, String brandHint) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("Debes subir una imagen para inferir");
        }
        String extension = mediaStorageService.resolveExtension(file);
        if (!Set.of("jpg", "png", "webp").contains(extension)) {
            throw new DomainException("Formato no soportado para inferencia IA: " + extension + ". Usa jpg, png o webp.");
        }
        try {
            byte[] bytes = file.getBytes();
            ProductAiOllamaClient.InferenceResult inference = ollamaClient.inferFromImage(
                    bytes,
                    file.getOriginalFilename(),
                    brandHint
            );
            return new ProductAiInferenceDto(
                    inference.title(),
                    inference.description(),
                    inference.imagePrompt(),
                    inference.engine()
            );
        } catch (Exception ex) {
            throw new DomainException("No se pudo inferir contenido con IA: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ProductAiJobDto getJob(UUID jobId) {
        ProductAiJobEntity job = requireJob(jobId);
        List<ProductAiOutputEntity> outputs = outputRepository.findByJobIdOrderByCreatedAtAsc(jobId);
        Map<UUID, ProductAiAssetEntity> assets = assetRepository.findByDraftIdOrderBySortOrderAscCreatedAtAsc(job.getDraftId())
                .stream()
                .collect(Collectors.toMap(ProductAiAssetEntity::getId, a -> a));

        List<ProductAiJobItemDto> items = outputs.stream()
                .map(output -> {
                    ProductAiAssetEntity asset = assets.get(output.getAssetId());
                    return new ProductAiJobItemDto(
                            output.getAssetId(),
                            output.getTitle(),
                            output.getDescription(),
                            output.getImagePrompt(),
                            asset == null ? null : asset.getProcessedMasterUrl(),
                            asset == null ? null : asset.getProcessedWebUrl(),
                            asset == null ? null : asset.getProcessedThumbUrl()
                    );
                })
                .toList();
        return toJobDto(job, items);
    }

    @Transactional(readOnly = true)
    public List<ProductAiJobSummaryDto> listJobs() {
        return jobRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(job -> new ProductAiJobSummaryDto(
                        job.getId(),
                        job.getDraftId(),
                        job.getStatus().name(),
                        job.getProgress(),
                        job.getAttempt(),
                        job.getMaxAttempts(),
                        job.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional
    public ProductAiJobDto retryJob(UUID jobId) {
        ProductAiJobEntity job = requireJob(jobId);
        if (job.getStatus() != ProductAiJobStatus.ERROR) {
            throw new DomainException("Only ERROR jobs can be retried");
        }
        if (job.getAttempt() >= job.getMaxAttempts()) {
            throw new DomainException("Max attempts reached");
        }
        job.setStatus(ProductAiJobStatus.PENDING);
        job.setProgress(0);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setNextRetryAt(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        return toJobDto(job, List.of());
    }

    @Transactional
    public ProductAiPublishResultDto approveAndPublish(UUID draftId, ApproveProductAiDraftRequest request) {
        ProductAiDraftEntity draft = requireDraft(draftId);
        if (draft.getStatus() == ProductAiDraftStatus.PUBLISHED && draft.getProductId() != null) {
            return new ProductAiPublishResultDto(draft.getProductId(), draft.getStatus().name(), draft.getUpdatedAt());
        }
        ProductAiJobEntity successJob = jobRepository.findFirstByDraftIdAndStatusOrderByFinishedAtDesc(
                        draftId,
                        ProductAiJobStatus.SUCCESS
                )
                .orElseThrow(() -> new DomainException("No successful job found for draft"));

        List<ProductAiAssetEntity> assets = assetRepository.findByDraftIdOrderBySortOrderAscCreatedAtAsc(draftId);
        if (assets.isEmpty()) {
            throw new DomainException("Draft has no assets");
        }

        ProductAiAssetEntity selectedAsset = resolveSelectedAsset(request, assets);
        ProductAiOutputEntity output = outputRepository.findByJobIdAndAssetId(successJob.getId(), selectedAsset.getId())
                .orElseGet(() -> outputRepository.findByJobIdOrderByCreatedAtAsc(successJob.getId()).stream().findFirst().orElse(null));

        String fallbackTitle = deriveTitleFromFilename(selectedAsset.getSourceFilename(), draft.getName());
        String name = nonBlank(
                request != null && request.override() != null ? request.override().name() : null,
                output != null ? output.getTitle() : null,
                draft.getName(),
                fallbackTitle
        );
        String description = nonBlank(
                request != null && request.override() != null ? request.override().description() : null,
                output != null ? output.getDescription() : null,
                "Producto generado desde pipeline IA"
        );
        String brand = nonBlank(
                request != null && request.override() != null ? request.override().brand() : null,
                draft.getBrand(),
                "Sin marca"
        );
        String condition = nonBlank(draft.getCondition(), "USED");
        BigDecimal priceAmount = draft.getPriceAmount() == null ? new BigDecimal("10000") : draft.getPriceAmount();
        String priceCurrency = nonBlank(draft.getPriceCurrency(), "CLP");
        String imageUrl = nonBlank(
                selectedAsset.getProcessedWebUrl(),
                selectedAsset.getProcessedMasterUrl(),
                selectedAsset.getOriginalUrl()
        );

        ProductDto product = createProductUseCase.execute(
                name,
                description,
                priceAmount,
                priceCurrency,
                null,
                null,
                imageUrl,
                condition,
                brand,
                1,
                true,
                null,
                null
        );

        draft.setProductId(product.id());
        draft.setStatus(ProductAiDraftStatus.PUBLISHED);
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);

        return new ProductAiPublishResultDto(product.id(), draft.getStatus().name(), draft.getUpdatedAt());
    }

    @Transactional
    public int processDueJobs() {
        if ("node_bridge".equalsIgnoreCase(productAiEngine)) {
            ProductAiOllamaClient.ReadinessStatus readiness = ollamaClient.checkReadiness();
            if (!readiness.ready()) {
                maybeLogReadinessWarning(readiness);
                return 0;
            }
            lastReadinessReason.set(null);
        }

        List<ProductAiJobEntity> dueJobs = jobRepository.findDuePending(Instant.now(), PageRequest.of(0, Math.max(workerBatchSize, 1)));
        int processed = 0;
        for (ProductAiJobEntity job : dueJobs) {
            processSingleJob(job);
            processed++;
        }
        return processed;
    }

    private void processSingleJob(ProductAiJobEntity job) {
        ProductAiDraftEntity draft = requireDraft(job.getDraftId());
        List<ProductAiAssetEntity> assets = assetRepository.findByDraftIdOrderBySortOrderAscCreatedAtAsc(job.getDraftId());
        if (assets.isEmpty()) {
            markJobError(job, "NO_ASSETS", "Draft has no assets");
            return;
        }

        try {
            job.setStatus(ProductAiJobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(1);
            job.setUpdatedAt(Instant.now());
            job.setErrorCode(null);
            job.setErrorMessage(null);
            jobRepository.save(job);

            outputRepository.deleteByJobId(job.getId());

            if ("node_bridge".equalsIgnoreCase(productAiEngine)) {
                processViaNodeBridge(job, draft, assets);
            } else {
                processViaStub(job, draft, assets);
            }

            job.setStatus(ProductAiJobStatus.SUCCESS);
            job.setProgress(100);
            job.setFinishedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);

            draft.setStatus(ProductAiDraftStatus.READY);
            draft.setUpdatedAt(Instant.now());
            draftRepository.save(draft);
        } catch (Exception ex) {
            log.error("product_ai_job_failed jobId={} draftId={}: {}", job.getId(), draft.getId(), ex.getMessage(), ex);
            markJobError(job, "PROCESSING_ERROR", ex.getMessage());
        }
    }

    private void processViaNodeBridge(ProductAiJobEntity job, ProductAiDraftEntity draft, List<ProductAiAssetEntity> assets) {
        Map<UUID, ProductAiNodeBridgeClient.TextInference> textInferences = new HashMap<>();
        for (ProductAiAssetEntity asset : assets) {
            byte[] sourceBytes = nodeBridgeClient.loadOriginalAssetBytes(asset.getOriginalUrl());
            ProductAiOllamaClient.InferenceResult inferred = ollamaClient.inferFromImage(
                    sourceBytes,
                    asset.getSourceFilename(),
                    draft.getBrand()
            );
            textInferences.put(
                    asset.getId(),
                    new ProductAiNodeBridgeClient.TextInference(
                            inferred.title(),
                            inferred.description(),
                            inferred.imagePrompt(),
                            inferred.rawResponseJson(),
                            inferred.engine()
                    )
            );
        }

        List<ProductAiNodeBridgeClient.NodeBridgeAssetResult> results =
                nodeBridgeClient.process(job.getId(), draft, assets, textInferences);
        if (results.size() != assets.size()) {
            throw new DomainException("Node bridge result count mismatch: expected " + assets.size() + " but got " + results.size());
        }

        Map<UUID, ProductAiNodeBridgeClient.NodeBridgeAssetResult> resultsByAssetId = results.stream()
                .collect(Collectors.toMap(ProductAiNodeBridgeClient.NodeBridgeAssetResult::assetId, item -> item));

        int total = assets.size();
        int index = 0;
        for (ProductAiAssetEntity asset : assets) {
            ProductAiNodeBridgeClient.NodeBridgeAssetResult result = resultsByAssetId.get(asset.getId());
            if (result == null) {
                throw new DomainException("Node bridge result missing for asset: " + asset.getId());
            }

            ProductAiOutputEntity output = new ProductAiOutputEntity();
            output.setId(UUID.randomUUID());
            output.setJobId(job.getId());
            output.setAssetId(asset.getId());
            output.setTitle(result.title());
            output.setDescription(result.description());
            output.setImagePrompt(result.imagePrompt());
            output.setRawResponseJson(result.rawResponseJson());
            output.setCreatedAt(Instant.now());
            outputRepository.save(output);

            asset.setProcessedMasterUrl(result.processedMasterUrl());
            asset.setProcessedWebUrl(result.processedWebUrl());
            asset.setProcessedThumbUrl(result.processedThumbUrl());
            assetRepository.save(asset);

            index++;
            int progress = Math.min(99, (int) Math.round((index * 100.0) / total));
            job.setProgress(progress);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void processViaStub(ProductAiJobEntity job, ProductAiDraftEntity draft, List<ProductAiAssetEntity> assets) {
        int total = assets.size();
        int index = 0;
        for (ProductAiAssetEntity asset : assets) {
            ProductAiOutputEntity output = buildOutput(job, draft, asset);
            outputRepository.save(output);

            asset.setProcessedMasterUrl(asset.getOriginalUrl());
            asset.setProcessedWebUrl(asset.getOriginalUrl());
            asset.setProcessedThumbUrl(asset.getOriginalUrl());
            assetRepository.save(asset);

            index++;
            int progress = Math.min(99, (int) Math.round((index * 100.0) / total));
            job.setProgress(progress);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private ProductAiOutputEntity buildOutput(ProductAiJobEntity job, ProductAiDraftEntity draft, ProductAiAssetEntity asset) {
        String title = deriveTitleFromFilename(asset.getSourceFilename(), draft.getName());
        String description = "Producto " + title + " generado para revision desde flujo IA.";
        String prompt = "Editorial ecommerce 4:5, sin texto, sin logos, fondo limpio, fidelidad de prenda.";

        ProductAiOutputEntity output = new ProductAiOutputEntity();
        output.setId(UUID.randomUUID());
        output.setJobId(job.getId());
        output.setAssetId(asset.getId());
        output.setTitle(title);
        output.setDescription(description);
        output.setImagePrompt(prompt);
        output.setRawResponseJson("{\"engine\":\"stub\",\"jobId\":\"" + job.getId() + "\"}");
        output.setCreatedAt(Instant.now());
        return output;
    }

    private void markJobError(ProductAiJobEntity job, String errorCode, String errorMessage) {
        int nextAttempt = job.getAttempt() + 1;
        job.setAttempt(nextAttempt);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setUpdatedAt(Instant.now());

        if (nextAttempt < job.getMaxAttempts()) {
            job.setStatus(ProductAiJobStatus.PENDING);
            job.setNextRetryAt(Instant.now().plusMillis(Math.max(250, retryBackoffMs)));
            job.setProgress(0);
        } else {
            job.setStatus(ProductAiJobStatus.ERROR);
            job.setFinishedAt(Instant.now());
        }
        jobRepository.save(job);
    }

    private ProductAiDraftEntity requireDraft(UUID draftId) {
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new NoSuchElementException("Product AI draft not found: " + draftId));
    }

    private ProductAiJobEntity requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Product AI job not found: " + jobId));
    }

    private ProductAiAssetEntity resolveSelectedAsset(ApproveProductAiDraftRequest request, List<ProductAiAssetEntity> assets) {
        if (request == null || request.selectedAssetId() == null) {
            return assets.get(0);
        }
        UUID selectedAssetId = request.selectedAssetId();
        return assets.stream()
                .filter(asset -> asset.getId().equals(selectedAssetId))
                .findFirst()
                .orElseThrow(() -> new DomainException("Selected asset does not belong to draft"));
    }

    private ProductAiJobDto toJobDto(ProductAiJobEntity job, List<ProductAiJobItemDto> items) {
        return new ProductAiJobDto(
                job.getId(),
                job.getDraftId(),
                job.getStatus().name(),
                job.getProgress(),
                job.getAttempt(),
                job.getMaxAttempts(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                items
        );
    }

    private String deriveTitleFromFilename(String sourceFilename, String fallback) {
        String candidate = sourceFilename == null ? "" : sourceFilename;
        int dotIdx = candidate.lastIndexOf('.');
        if (dotIdx > 0) {
            candidate = candidate.substring(0, dotIdx);
        }
        candidate = candidate.replace('_', ' ').replace('-', ' ').trim();
        if (candidate.isBlank()) {
            candidate = fallback == null || fallback.isBlank() ? "Producto IA" : fallback.trim();
        }
        if (candidate.length() > 90) {
            candidate = candidate.substring(0, 90).trim();
        }
        return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
    }

    private String sanitizeFolderSegment(String segment) {
        String normalized = segment.toLowerCase(Locale.ROOT).replace("\\", "/");
        normalized = normalized.replaceAll("[^a-z0-9/_-]", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("(^/+|/+$)", "");
        return normalized.isBlank() ? "lote" : normalized;
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void maybeLogReadinessWarning(ProductAiOllamaClient.ReadinessStatus readiness) {
        Instant now = Instant.now();
        String reason = readiness.reason();
        String previousReason = lastReadinessReason.get();
        boolean reasonChanged = !Objects.equals(previousReason, reason);
        boolean shouldLogByTime = lastReadinessLogAt == null || now.isAfter(lastReadinessLogAt.plusSeconds(90));
        if (reasonChanged || shouldLogByTime) {
            log.warn(
                    "product_ai_worker_paused ollama_ready={} ollama_reachable={} model_available={} reason={} model={} base_url={}",
                    readiness.ready(),
                    readiness.reachable(),
                    readiness.modelAvailable(),
                    reason,
                    readiness.model(),
                    readiness.baseUrl()
            );
            lastReadinessReason.set(reason);
            lastReadinessLogAt = now;
        }
    }
}
