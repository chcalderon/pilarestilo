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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProductAiService {

    private static final Logger log = LoggerFactory.getLogger(ProductAiService.class);
    private static final String DEFAULT_PRODUCT_TRANSFORM_PROMPT = "Generar una imagen de tamano ideal para Instagram (la presenta una modelo en un fondo de boutique de lujo), para campana de invierno. Respetar estrictamente diseno, color, textura y corte de la prenda. Entregar sin texto, sin logos, sin marcas de agua, formato vertical 4:5.";

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

    @Value("${app.product-ai.ollama.infer-max-dimension:1024}")
    private int ollamaInferMaxDimension;

    @Value("${app.product-ai.ollama.infer-jpeg-quality:0.82}")
    private float ollamaInferJpegQuality;

    @Value("${app.product-ai.ollama.model:gemma3}")
    private String ollamaPrimaryModel;

    @Value("${app.product-ai.ollama.quality-fallback-enabled:true}")
    private boolean ollamaQualityFallbackEnabled;

    @Value("${app.product-ai.ollama.quality-fallback-model:gemma3}")
    private String ollamaQualityFallbackModel;

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
            byte[] bytes = optimizeImageForOllamaInference(file.getBytes());
            ProductAiOllamaClient.InferenceResult inference = inferWithQualityFallback(
                    bytes,
                    file.getOriginalFilename(),
                    brandHint
            );
            return new ProductAiInferenceDto(
                    inference.title(),
                    inference.description(),
                    inference.imagePrompt(),
                    inference.engine(),
                    inference.fallbackReason()
            );
        } catch (Exception ex) {
            throw new DomainException("No se pudo inferir contenido con IA: " + ex.getMessage());
        }
    }

    public ProductAiImageTransformDto transformSingleImage(
            MultipartFile file,
            String providerRaw,
            String promptOverride,
            String brandHint
    ) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("Debes subir una imagen para transformar");
        }
        String extension = mediaStorageService.resolveExtension(file);
        if (!Set.of("jpg", "png", "webp").contains(extension)) {
            throw new DomainException("Formato no soportado para transformacion IA: " + extension + ". Usa jpg, png o webp.");
        }

        String provider = normalizeTransformProvider(providerRaw);
        if ("ollama".equals(provider)) {
            throw new DomainException("Transformacion de imagen con Ollama aun no soportada en este pipeline. Usa OpenAI para transformar; Ollama sigue activo para inferir texto.");
        }

        String prompt = resolveTransformPrompt(promptOverride, brandHint);
        try {
            byte[] bytes = file.getBytes();
            if (!"node_bridge".equalsIgnoreCase(productAiEngine)) {
                UUID requestId = UUID.randomUUID();
                ProductAiNodeBridgeClient.StoredDerivatives derivatives = nodeBridgeClient.storeDerivatives(
                        "products/ai/single/" + shortId(requestId),
                        "single-" + shortId(requestId),
                        bytes
                );
                return new ProductAiImageTransformDto(
                        derivatives.masterUrl(),
                        derivatives.webUrl(),
                        derivatives.thumbUrl(),
                        "BACKEND_PASSTHROUGH",
                        prompt,
                        "backend"
                );
            }
            ProductAiNodeBridgeClient.SingleTransformResult result = nodeBridgeClient.transformSingleImage(
                    UUID.randomUUID(),
                    bytes,
                    file.getOriginalFilename(),
                    provider,
                    prompt
            );
            return new ProductAiImageTransformDto(
                    result.processedMasterUrl(),
                    result.processedWebUrl(),
                    result.processedThumbUrl(),
                    provider.toUpperCase(Locale.ROOT),
                    prompt,
                    "node_bridge"
            );
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("No se pudo transformar la imagen con IA: " + ex.getMessage());
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
        if (usesOllamaEngine()) {
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
            } else if ("ollama_backend".equalsIgnoreCase(productAiEngine)) {
                processViaBackendOllama(job, draft, assets);
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

    private void processViaBackendOllama(ProductAiJobEntity job, ProductAiDraftEntity draft, List<ProductAiAssetEntity> assets) {
        int total = assets.size();
        int index = 0;
        for (ProductAiAssetEntity asset : assets) {
            byte[] sourceBytes = nodeBridgeClient.loadOriginalAssetBytes(asset.getOriginalUrl());
            ProductAiOllamaClient.InferenceResult inferred = inferWithQualityFallback(
                    optimizeImageForOllamaInference(sourceBytes),
                    asset.getSourceFilename(),
                    draft.getBrand()
            );

            ProductAiOutputEntity output = new ProductAiOutputEntity();
            output.setId(UUID.randomUUID());
            output.setJobId(job.getId());
            output.setAssetId(asset.getId());
            output.setTitle(inferred.title());
            output.setDescription(inferred.description());
            output.setImagePrompt(inferred.imagePrompt());
            output.setRawResponseJson(inferred.rawResponseJson());
            output.setCreatedAt(Instant.now());
            outputRepository.save(output);

            // Backend-only mode keeps original media URLs and avoids external node transform pipeline.
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

    private void processViaNodeBridge(ProductAiJobEntity job, ProductAiDraftEntity draft, List<ProductAiAssetEntity> assets) {
        Map<UUID, ProductAiNodeBridgeClient.TextInference> textInferences = new HashMap<>();
        for (ProductAiAssetEntity asset : assets) {
            byte[] sourceBytes = nodeBridgeClient.loadOriginalAssetBytes(asset.getOriginalUrl());
            ProductAiOllamaClient.InferenceResult inferred = inferWithQualityFallback(
                    optimizeImageForOllamaInference(sourceBytes),
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

    private String normalizeTransformProvider(String providerRaw) {
        String normalized = providerRaw == null ? "" : providerRaw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "openai";
        }
        if (!Set.of("openai", "ollama").contains(normalized)) {
            throw new DomainException("Proveedor IA no soportado: " + providerRaw + ". Usa OPENAI u OLLAMA.");
        }
        return normalized;
    }

    private String resolveTransformPrompt(String promptOverride, String brandHint) {
        String prompt = promptOverride == null ? "" : promptOverride.trim();
        if (prompt.isBlank()) {
            prompt = DEFAULT_PRODUCT_TRANSFORM_PROMPT;
            if (brandHint != null && !brandHint.isBlank()) {
                prompt += " Marca sugerida: " + brandHint.trim() + ".";
            }
        }
        if (prompt.length() > 1600) {
            prompt = prompt.substring(0, 1600).trim();
        }
        return prompt;
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private ProductAiOllamaClient.InferenceResult inferWithQualityFallback(
            byte[] imageBytes,
            String sourceFilename,
            String brandHint
    ) {
        ProductAiOllamaClient.InferenceResult primary = ollamaClient.inferFromImage(
                imageBytes,
                sourceFilename,
                brandHint,
                ollamaPrimaryModel
        );
        if (isInferenceQualityAcceptable(primary)) {
            return primary;
        }
        if (isInfrastructureFailure(primary)) {
            return primary;
        }

        String fallbackModel = normalizeModelName(ollamaQualityFallbackModel);
        String primaryModel = normalizeModelName(ollamaPrimaryModel);
        if (!ollamaQualityFallbackEnabled || fallbackModel.isBlank() || fallbackModel.equals(primaryModel)) {
            log.warn(
                    "product_ai_ollama_quality_gate_bypassed source={} model={} reason=no_fallback_configured title='{}'",
                    sourceFilename,
                    primaryModel,
                    normalizeText(primary.title())
            );
            return new ProductAiOllamaClient.InferenceResult(
                    primary.title(),
                    primary.description(),
                    primary.imagePrompt(),
                    primary.rawResponseJson(),
                    primary.engine(),
                    "quality-warning-no-fallback"
            );
        }

        log.info(
                "product_ai_ollama_quality_fallback source={} primary_model={} fallback_model={} primary_engine={} primary_reason={}",
                sourceFilename,
                primaryModel,
                fallbackModel,
                primary.engine(),
                primary.fallbackReason()
        );

        ProductAiOllamaClient.InferenceResult secondary = ollamaClient.inferFromImage(
                imageBytes,
                sourceFilename,
                brandHint,
                fallbackModel
        );

        if (isInferenceQualityAcceptable(secondary)) {
            return new ProductAiOllamaClient.InferenceResult(
                    secondary.title(),
                    secondary.description(),
                    secondary.imagePrompt(),
                    secondary.rawResponseJson(),
                    secondary.engine(),
                    "quality-fallback:" + primaryModel + "->" + fallbackModel
            );
        }

        return new ProductAiOllamaClient.InferenceResult(
                secondary.title(),
                secondary.description(),
                secondary.imagePrompt(),
                secondary.rawResponseJson(),
                "ollama-fallback",
                "quality-rejected:" + primaryModel + "->" + fallbackModel
        );
    }

    private boolean isInfrastructureFailure(ProductAiOllamaClient.InferenceResult inference) {
        if (inference == null) {
            return false;
        }
        String reason = normalizeText(inference.fallbackReason()).toLowerCase(Locale.ROOT);
        if (reason.isBlank()) {
            return false;
        }
        return reason.startsWith("ollama-timeout")
                || reason.startsWith("ollama-http-")
                || reason.startsWith("ollama-error")
                || reason.startsWith("ollama-unreachable");
    }

    private boolean usesOllamaEngine() {
        return "node_bridge".equalsIgnoreCase(productAiEngine)
                || "ollama_backend".equalsIgnoreCase(productAiEngine);
    }

    private String shortId(UUID id) {
        return id == null ? "na" : id.toString().substring(0, 8);
    }

    private boolean isInferenceQualityAcceptable(ProductAiOllamaClient.InferenceResult inference) {
        if (inference == null) {
            return false;
        }
        if ("ollama-fallback".equalsIgnoreCase(nonBlank(inference.engine()))) {
            return false;
        }

        String title = normalizeText(inference.title());
        String description = normalizeText(inference.description());

        if (title.length() < 8 || countWords(title) < 2) {
            return false;
        }
        if (description.length() < 35 || countWords(description) < 7) {
            return false;
        }
        if (looksLikeGenericDescription(description)) {
            return false;
        }
        if (looksLikeFilenameOrHashTitle(title)) {
            return false;
        }

        String combined = (title + " " + description).toLowerCase(Locale.ROOT);
        List<String> bannedTokens = List.of(
                "2 frases",
                "dos frases",
                "maximo",
                "máximo",
                "corto",
                "placeholder",
                "n/a",
                "completa",
                "rellenar",
                "rellena",
                "lista para publicar",
                "revisar y ajustar detalles finales",
                "titulo sugerido",
                "descripcion sugerida"
        );
        for (String token : bannedTokens) {
            if (combined.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeGenericDescription(String description) {
        String normalized = description.toLowerCase(Locale.ROOT);
        List<String> genericPatterns = List.of(
                "prenda de boutique en excelente estado, lista para publicar",
                "prenda seleccionada para catalogo boutique",
                "revisar y ajustar detalles finales antes de publicar"
        );
        for (String pattern : genericPatterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeFilenameOrHashTitle(String title) {
        String normalized = title.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return true;
        }

        if (normalized.matches("^[0-9a-f\\s-]{12,}$")) {
            return true;
        }

        if (normalized.matches(".*\\b[0-9a-f]{8}\\b.*") && normalized.matches(".*\\b[0-9a-f]{4}\\b.*")) {
            return true;
        }

        int alphaCount = 0;
        int digitCount = 0;
        for (char c : normalized.toCharArray()) {
            if (Character.isLetter(c)) {
                alphaCount++;
            } else if (Character.isDigit(c)) {
                digitCount++;
            }
        }
        if (digitCount >= 10 && alphaCount <= 4) {
            return true;
        }

        return false;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private int countWords(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(value.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null) {
            return "";
        }
        return modelName.trim().toLowerCase(Locale.ROOT);
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

    private byte[] optimizeImageForOllamaInference(byte[] sourceBytes) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            return sourceBytes;
        }

        int maxDimension = Math.max(256, ollamaInferMaxDimension);
        float jpegQuality = Math.min(1.0f, Math.max(0.5f, ollamaInferJpegQuality));
        try {
            BufferedImage input = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (input == null) {
                return sourceBytes;
            }

            int width = Math.max(1, input.getWidth());
            int height = Math.max(1, input.getHeight());
            double scale = Math.min(1.0d, (double) maxDimension / (double) Math.max(width, height));

            BufferedImage working = toRgbImage(input);
            if (scale < 0.999d) {
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));
                BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.drawImage(working, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    graphics.dispose();
                }
                working = resized;
            }

            return writeJpeg(working, jpegQuality);
        } catch (Exception ex) {
            log.warn("product_ai_ollama_preprocess_failed reason={}", ex.getMessage());
            return sourceBytes;
        }
    }

    private BufferedImage toRgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream fallback = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", fallback);
            return fallback.toByteArray();
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
