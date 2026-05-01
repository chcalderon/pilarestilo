package com.pilarestilo.productai.infrastructure.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiAssetEntity;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiDraftEntity;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ProductAiNodeBridgeClient {

    private static final Logger log = LoggerFactory.getLogger(ProductAiNodeBridgeClient.class);
    private static final Set<String> ALLOWED_SOURCE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final ObjectMapper objectMapper;
    private final MediaStorageService mediaStorageService;
    private final Path mediaRoot;

    @Value("${app.product-ai.node.command:node}")
    private String nodeCommand;

    @Value("${app.product-ai.node.project-path:}")
    private String nodeProjectPath;

    @Value("${app.product-ai.node.generate-script:generate-prompts.js}")
    private String generateScriptName;

    @Value("${app.product-ai.node.run-generate-prompts:false}")
    private boolean runGeneratePrompts;

    @Value("${app.product-ai.node.transform-script:transform-images.js}")
    private String transformScriptName;

    @Value("${app.product-ai.node.workspace-root:./tmp/product-ai}")
    private String workspaceRootPath;

    @Value("${app.product-ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.product-ai.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${app.product-ai.openai.model:gpt-image-1}")
    private String openAiImageModel;

    @Value("${app.product-ai.timeout-ms:60000}")
    private long timeoutMs;

    @Value("${app.product-ai.image.target-width:1024}")
    private int targetWidth;

    @Value("${app.product-ai.image.target-height:1280}")
    private int targetHeight;

    @Value("${app.product-ai.image.web-width:1024}")
    private int webWidth;

    @Value("${app.product-ai.image.web-height:1280}")
    private int webHeight;

    @Value("${app.product-ai.image.web-jpeg-quality:0.88}")
    private float webJpegQuality;

    @Value("${app.product-ai.image.thumb-width:320}")
    private int thumbWidth;

    @Value("${app.product-ai.image.thumb-height:400}")
    private int thumbHeight;

    @Value("${app.product-ai.image.thumb-jpeg-quality:0.82}")
    private float thumbJpegQuality;

    public ProductAiNodeBridgeClient(
            ObjectMapper objectMapper,
            MediaStorageService mediaStorageService,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath
    ) {
        this.objectMapper = objectMapper;
        this.mediaStorageService = mediaStorageService;
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    public List<NodeBridgeAssetResult> process(
            UUID jobId,
            ProductAiDraftEntity draft,
            List<ProductAiAssetEntity> assets,
            Map<UUID, TextInference> textInferenceByAsset
    ) {
        if (nodeProjectPath == null || nodeProjectPath.isBlank()) {
            throw new DomainException("Node bridge is enabled but app.product-ai.node.project-path is empty");
        }
        if (assets.isEmpty()) {
            return List.of();
        }

        Path projectPath = Paths.get(nodeProjectPath).toAbsolutePath().normalize();
        Path generateScript = projectPath.resolve(generateScriptName).normalize();
        Path transformScript = projectPath.resolve(transformScriptName).normalize();
        validateBridgePaths(projectPath, generateScript, transformScript);

        Path workspaceRoot = Paths.get(workspaceRootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException ex) {
            throw new DomainException("Could not create Product AI workspace root: " + workspaceRoot);
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory(workspaceRoot, "job-" + jobId + "-");
            Path inputDir = workspace.resolve("input-images");
            Path outputDir = workspace.resolve("output-images");
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);

            List<StagedAsset> stagedAssets = stageInputAssets(jobId, draft, assets, inputDir);

            List<PromptItem> promptItems = List.of();
            if (runGeneratePrompts) {
                writeProductsJson(draft, stagedAssets, workspace.resolve("products.json"));
                runNodeScript(
                        jobId,
                        workspace,
                        generateScript,
                        List.of("--mode=manual"),
                        Map.of()
                );
                promptItems = loadPromptItems(workspace.resolve("output").resolve("prompts-generated.json"));
            }

            runNodeScript(
                    jobId,
                    workspace,
                    transformScript,
                    List.of(),
                    buildTransformEnv(inputDir, outputDir, "openai", null)
            );

            return uploadAndBuildResults(jobId, draft, stagedAssets, promptItems, textInferenceByAsset, outputDir);
        } catch (IOException ex) {
            throw new DomainException("Node bridge IO error for Product AI job " + jobId + ": " + ex.getMessage());
        } finally {
            if (workspace != null) {
                deleteRecursivelyQuietly(workspace);
            }
        }
    }

    private void validateBridgePaths(Path projectPath, Path generateScript, Path transformScript) {
        if (!Files.isDirectory(projectPath)) {
            throw new DomainException("Node bridge project path does not exist: " + projectPath);
        }
        if (runGeneratePrompts && !Files.exists(generateScript)) {
            throw new DomainException("Node bridge script not found: " + generateScript);
        }
        if (!Files.exists(transformScript)) {
            throw new DomainException("Node bridge script not found: " + transformScript);
        }
    }

    private List<StagedAsset> stageInputAssets(
            UUID jobId,
            ProductAiDraftEntity draft,
            List<ProductAiAssetEntity> assets,
            Path inputDir
    ) throws IOException {
        List<StagedAsset> staged = new ArrayList<>();
        int index = 0;
        for (ProductAiAssetEntity asset : assets) {
            String extension = resolveSourceExtension(asset);
            byte[] bytes = loadOriginalAssetBytes(asset.getOriginalUrl());
            String baseName = String.format(Locale.ROOT, "asset-%02d-%s", index + 1, shortId(asset.getId()));
            Path inputPath = inputDir.resolve(baseName + "." + extension);
            Files.write(inputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String outputName = baseName + "_marketplace.png";
            staged.add(new StagedAsset(asset, baseName, inputPath, outputName));
            index++;
        }

        log.info("product_ai_node_bridge_staged jobId={} draftId={} assets={}", jobId, draft.getId(), staged.size());
        return staged;
    }

    private void writeProductsJson(ProductAiDraftEntity draft, List<StagedAsset> stagedAssets, Path outputFile) throws IOException {
        List<Map<String, Object>> products = new ArrayList<>();
        int idx = 0;
        for (StagedAsset stagedAsset : stagedAssets) {
            ProductAiAssetEntity asset = stagedAsset.asset();
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("name", nonBlank(cleanTitleCandidate(asset.getSourceFilename()), draft.getName(), "Prenda boutique"));
            product.put("category", "Prenda");
            product.put("size", "Unica");
            product.put("price", draft.getPriceAmount() == null ? 10000 : draft.getPriceAmount().intValue());
            product.put("garmentDescription", deriveGarmentDescription(asset, draft.getBrand()));
            product.put("skuHint", stagedAsset.baseName());
            product.put("position", idx + 1);
            products.add(product);
            idx++;
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), products);
    }

    private List<PromptItem> loadPromptItems(Path outputJson) {
        if (!Files.exists(outputJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(outputJson.toFile(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("product_ai_prompt_parse_failed file={} reason={}", outputJson, ex.getMessage());
            return List.of();
        }
    }

    private Map<String, String> buildTransformEnv(Path inputDir, Path outputDir, String provider, String promptOverride) {
        Map<String, String> env = new HashMap<>();
        env.put("INPUT_IMAGES_DIR", inputDir.toAbsolutePath().toString());
        env.put("OUTPUT_IMAGES_DIR", outputDir.toAbsolutePath().toString());
        env.put("FINAL_OUTPUT_WIDTH", Integer.toString(Math.max(targetWidth, 1)));
        env.put("FINAL_OUTPUT_HEIGHT", Integer.toString(Math.max(targetHeight, 1)));
        env.put("IMAGE_API_PROVIDER", nonBlank(provider, "openai"));
        env.put("IMAGE_API_BASE_URL", openAiBaseUrl);
        env.put("IMAGE_EDIT_MODEL", nonBlank(openAiImageModel, "gpt-image-1"));
        env.put("REQUEST_TIMEOUT_MS", Long.toString(Math.max(timeoutMs, 1_000)));
        env.put("TRANSFORM_DELAY_MS", "250");
        env.put("DEBUG_PROMPT", "false");
        env.put("MAX_IMAGES", "0");
        if (promptOverride != null && !promptOverride.isBlank()) {
            env.put("IMAGE_EDIT_PROMPT_OVERRIDE", promptOverride.trim());
        }
        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            env.put("OPENAI_API_KEY", openAiApiKey);
            env.put("IMAGE_API_KEY", openAiApiKey);
        }
        return env;
    }

    public SingleTransformResult transformSingleImage(
            UUID requestId,
            byte[] sourceImageBytes,
            String sourceFilename,
            String provider,
            String promptOverride
    ) {
        if (nodeProjectPath == null || nodeProjectPath.isBlank()) {
            throw new DomainException("Node bridge is enabled but app.product-ai.node.project-path is empty");
        }
        if (sourceImageBytes == null || sourceImageBytes.length == 0) {
            throw new DomainException("No se recibio imagen para transformar");
        }

        Path projectPath = Paths.get(nodeProjectPath).toAbsolutePath().normalize();
        Path transformScript = projectPath.resolve(transformScriptName).normalize();
        validateBridgePaths(projectPath, projectPath.resolve(generateScriptName).normalize(), transformScript);

        String sourceExtension = resolveSourceExtension(sourceFilename);
        Path workspaceRoot = Paths.get(workspaceRootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException ex) {
            throw new DomainException("Could not create Product AI workspace root: " + workspaceRoot);
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory(workspaceRoot, "single-" + shortId(requestId) + "-");
            Path inputDir = workspace.resolve("input-images");
            Path outputDir = workspace.resolve("output-images");
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);

            String baseName = "single-" + shortId(requestId);
            String sourceName = baseName + "." + sourceExtension;
            Path inputPath = inputDir.resolve(sourceName);
            Files.write(inputPath, sourceImageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            runNodeScript(
                    requestId,
                    workspace,
                    transformScript,
                    List.of(),
                    buildTransformEnv(inputDir, outputDir, provider, promptOverride)
            );

            String outputName = toMarketplaceName(sourceName);
            Path outputPath = outputDir.resolve(outputName);
            if (!Files.exists(outputPath)) {
                throw new DomainException("Node bridge no genero imagen transformada");
            }

            byte[] masterBytes = Files.readAllBytes(outputPath);
            String folder = "products/ai/single/" + shortId(requestId);
            String prefix = "single-" + shortId(requestId);
            StoredDerivatives derivatives = storeDerivatives(folder, prefix, masterBytes);
            return new SingleTransformResult(
                    derivatives.masterUrl(),
                    derivatives.webUrl(),
                    derivatives.thumbUrl()
            );
        } catch (IOException ex) {
            throw new DomainException("Node bridge IO error en transformacion single: " + ex.getMessage());
        } finally {
            if (workspace != null) {
                deleteRecursivelyQuietly(workspace);
            }
        }
    }

    private List<NodeBridgeAssetResult> uploadAndBuildResults(
            UUID jobId,
            ProductAiDraftEntity draft,
            List<StagedAsset> stagedAssets,
            List<PromptItem> promptItems,
            Map<UUID, TextInference> textInferenceByAsset,
            Path outputDir
    ) throws IOException {
        List<NodeBridgeAssetResult> results = new ArrayList<>();
        int idx = 0;
        for (StagedAsset stagedAsset : stagedAssets) {
            ProductAiAssetEntity asset = stagedAsset.asset();
            Path outputPath = outputDir.resolve(stagedAsset.expectedOutputName());
            if (!Files.exists(outputPath)) {
                throw new DomainException("Node bridge did not generate output image for asset " + asset.getId());
            }
            byte[] masterBytes = Files.readAllBytes(outputPath);
            byte[] webBytes = toJpeg(masterBytes, Math.max(webWidth, 1), Math.max(webHeight, 1), clampQuality(webJpegQuality));
            byte[] thumbBytes = toJpeg(masterBytes, Math.max(thumbWidth, 1), Math.max(thumbHeight, 1), clampQuality(thumbJpegQuality));

            String folder = "products/ai/processed/" + draft.getId();
            String prefix = "job-" + shortId(jobId) + "-" + shortId(asset.getId());
            String masterUrl = storeBytes(folder, prefix + "-master.png", "image/png", masterBytes);
            String webUrl = storeBytes(folder, prefix + "-web.jpg", "image/jpeg", webBytes);
            String thumbUrl = storeBytes(folder, prefix + "-thumb.jpg", "image/jpeg", thumbBytes);

            PromptItem promptItem = idx < promptItems.size() ? promptItems.get(idx) : null;
            TextInference inference = textInferenceByAsset == null ? null : textInferenceByAsset.get(asset.getId());
            String title = nonBlank(
                    inference != null ? inference.title() : null,
                    promptItem != null ? promptItem.title() : null,
                    cleanTitleCandidate(asset.getSourceFilename()),
                    "Producto IA"
            );
            String description = nonBlank(
                    inference != null ? inference.description() : null,
                    promptItem != null ? promptItem.description() : null,
                    "Producto generado para revision desde pipeline IA."
            );
            String imagePrompt = nonBlank(
                    inference != null ? inference.imagePrompt() : null,
                    promptItem != null ? promptItem.imagePrompt() : null,
                    "Editorial ecommerce 4:5, sin texto, sin logos, fondo limpio, fidelidad de prenda."
            );

            String rawJson = objectMapper.writeValueAsString(Map.of(
                    "engine", "node_bridge",
                    "input", stagedAsset.inputPath().getFileName().toString(),
                    "output", stagedAsset.expectedOutputName(),
                    "provider", "openai-images-edits",
                    "textEngine", inference != null ? inference.engine() : (promptItem != null ? "node_generate_prompts" : "fallback"),
                    "textRaw", inference != null ? inference.rawResponseJson() : null
            ));

            results.add(new NodeBridgeAssetResult(asset.getId(), title, description, imagePrompt, masterUrl, webUrl, thumbUrl, rawJson));
            idx++;
        }
        return results;
    }

    private String storeBytes(String folder, String filename, String contentType, byte[] data) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            return mediaStorageService.storeRaw(in, folder, filename, contentType);
        } catch (IOException ex) {
            throw new DomainException("Could not store processed AI image: " + ex.getMessage());
        }
    }

    public StoredDerivatives storeDerivatives(String folder, String prefix, byte[] masterBytes) {
        byte[] webBytes = toJpeg(masterBytes, Math.max(webWidth, 1), Math.max(webHeight, 1), clampQuality(webJpegQuality));
        byte[] thumbBytes = toJpeg(masterBytes, Math.max(thumbWidth, 1), Math.max(thumbHeight, 1), clampQuality(thumbJpegQuality));
        String masterUrl = storeBytes(folder, prefix + "-master.png", "image/png", masterBytes);
        String webUrl = storeBytes(folder, prefix + "-web.jpg", "image/jpeg", webBytes);
        String thumbUrl = storeBytes(folder, prefix + "-thumb.jpg", "image/jpeg", thumbBytes);
        return new StoredDerivatives(masterUrl, webUrl, thumbUrl);
    }

    private String resolveSourceExtension(ProductAiAssetEntity asset) {
        return resolveSourceExtension(asset == null ? null : asset.getSourceFilename());
    }

    private String resolveSourceExtension(String sourceFilename) {
        String filename = sourceFilename == null ? "" : sourceFilename.toLowerCase(Locale.ROOT).trim();
        int dotIdx = filename.lastIndexOf('.');
        String ext = dotIdx > -1 && dotIdx < filename.length() - 1 ? filename.substring(dotIdx + 1) : "";
        if ("jpeg".equals(ext)) {
            ext = "jpg";
        }
        if (!ALLOWED_SOURCE_EXTENSIONS.contains(ext)) {
            throw new DomainException("Unsupported image format for Product AI: " + nonBlank(ext, "unknown"));
        }
        return ext;
    }

    private String toMarketplaceName(String sourceName) {
        int dotIdx = sourceName == null ? -1 : sourceName.lastIndexOf('.');
        String base = dotIdx > 0 ? sourceName.substring(0, dotIdx) : nonBlank(sourceName, "image");
        return base + "_marketplace.png";
    }

    public byte[] loadOriginalAssetBytes(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new DomainException("Asset URL is empty");
        }
        try {
            if (originalUrl.startsWith("/api/media/")) {
                String relative = originalUrl.substring("/api/media/".length());
                Path target = mediaRoot.resolve(relative).normalize();
                if (!target.startsWith(mediaRoot)) {
                    throw new DomainException("Invalid local media path");
                }
                return Files.readAllBytes(target);
            }
            URL url = URI.create(originalUrl).toURL();
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ex) {
            throw new DomainException("Could not read source image: " + ex.getMessage());
        }
    }

    private void runNodeScript(
            UUID jobId,
            Path workDir,
            Path scriptPath,
            List<String> args,
            Map<String, String> extraEnv
    ) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(nodeCommand);
        command.add(scriptPath.toString());
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Path logFile = workDir.resolve(scriptPath.getFileName().toString() + ".log");
        pb.redirectOutput(logFile.toFile());
        Map<String, String> env = pb.environment();
        env.putAll(extraEnv);

        Process process = pb.start();
        long timeout = Math.max(timeoutMs, 60_000);
        boolean finished;
        try {
            finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new DomainException("Node bridge script interrupted");
        }

        String stdout = Files.exists(logFile)
                ? Files.readString(logFile, StandardCharsets.UTF_8)
                : "";
        if (!finished) {
            process.destroyForcibly();
            throw new DomainException("Node bridge script timeout for " + scriptPath.getFileName() + " after " + timeout + "ms");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new DomainException("Node bridge script failed (" + scriptPath.getFileName() + "): " + summarizeStdout(stdout));
        }
        log.info("product_ai_node_bridge_ok jobId={} script={} output={}", jobId, scriptPath.getFileName(), summarizeStdout(stdout));
    }

    private byte[] toJpeg(byte[] sourceBytes, int targetWidth, int targetHeight, float quality) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        } catch (IOException ex) {
            throw new DomainException("Could not decode generated image for JPEG resize");
        }
        if (source == null) {
            throw new DomainException("Generated image could not be decoded");
        }
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.min(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
            int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (targetWidth - drawW) / 2;
            int y = (targetHeight - drawH) / 2;
            g.drawImage(source, x, y, drawW, drawH, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(canvas, null, null), params);
        } catch (IOException ex) {
            throw new DomainException("Could not encode JPEG derivative: " + ex.getMessage());
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String deriveGarmentDescription(ProductAiAssetEntity asset, String brand) {
        String source = cleanTitleCandidate(asset.getSourceFilename()).toLowerCase(Locale.ROOT);
        String normalized = source.replaceAll("\\s+", " ").trim();
        String prefix = brand == null || brand.isBlank() ? "" : (brand.trim() + " ");
        if (normalized.isBlank()) {
            return prefix + "prenda de boutique en excelente estado";
        }
        return prefix + normalized;
    }

    private String cleanTitleCandidate(String sourceFilename) {
        if (sourceFilename == null || sourceFilename.isBlank()) {
            return "";
        }
        String candidate = sourceFilename;
        int dotIdx = candidate.lastIndexOf('.');
        if (dotIdx > 0) {
            candidate = candidate.substring(0, dotIdx);
        }
        candidate = candidate.replace('_', ' ').replace('-', ' ').trim();
        if (candidate.isBlank()) {
            return "";
        }
        if (candidate.length() > 90) {
            candidate = candidate.substring(0, 90).trim();
        }
        return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
    }

    private String summarizeStdout(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "(empty)";
        }
        String compact = stdout.replace('\r', ' ').replace('\n', ' ').trim();
        return compact.length() > 320 ? compact.substring(0, 320) + "..." : compact;
    }

    private float clampQuality(float quality) {
        if (Float.isNaN(quality)) return 0.85f;
        if (quality < 0.1f) return 0.1f;
        return Math.min(quality, 1.0f);
    }

    private String shortId(UUID id) {
        return id == null ? "na" : id.toString().substring(0, 8);
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void deleteRecursivelyQuietly(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record StagedAsset(
            ProductAiAssetEntity asset,
            String baseName,
            Path inputPath,
            String expectedOutputName
    ) {
    }

    private record PromptItem(
            String title,
            String description,
            String imagePrompt
    ) {
    }

    public record NodeBridgeAssetResult(
            UUID assetId,
            String title,
            String description,
            String imagePrompt,
            String processedMasterUrl,
            String processedWebUrl,
            String processedThumbUrl,
            String rawResponseJson
    ) {
    }

    public record TextInference(
            String title,
            String description,
            String imagePrompt,
            String rawResponseJson,
            String engine
    ) {
    }

    public record SingleTransformResult(
            String processedMasterUrl,
            String processedWebUrl,
            String processedThumbUrl
    ) {
    }

    public record StoredDerivatives(
            String masterUrl,
            String webUrl,
            String thumbUrl
    ) {
    }
}
