package com.pilarestilo.shared.infrastructure.web.controllers;

import com.pilarestilo.category.application.usecases.MigrateCategoryImagesUseCase;
import com.pilarestilo.category.application.usecases.OptimizeCategoryImagesUseCase;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.application.usecases.OptimizeProductImagesUseCase;
import com.pilarestilo.shared.infrastructure.services.ImageOptimizerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/admin/media")
public class MediaAdminController {

    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> SKIP_FOLDERS = Set.of("products", "categories");
    private static final Set<String> SOURCE_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "avif");
    private static final String HERO_MODELS_FOLDER = "hero-models";
    private static final String LEFT_SLOT = "left";
    private static final String RIGHT_SLOT = "right";
    private static final String HERO_LEFT_FILENAME = "hero-left.png";
    private static final String HERO_RIGHT_FILENAME = "hero-right.png";

    private final MigrateCategoryImagesUseCase migrateUseCase;
    private final OptimizeProductImagesUseCase optimizeProductImagesUseCase;
    private final OptimizeCategoryImagesUseCase optimizeCategoryImagesUseCase;
    private final ProductRepository productRepository;
    private final ImageOptimizerService imageOptimizer;
    private final HttpClient httpClient;
    private final Path mediaRoot;

    public MediaAdminController(
            MigrateCategoryImagesUseCase migrateUseCase,
            OptimizeProductImagesUseCase optimizeProductImagesUseCase,
            OptimizeCategoryImagesUseCase optimizeCategoryImagesUseCase,
            ProductRepository productRepository,
            ImageOptimizerService imageOptimizer,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.migrateUseCase = migrateUseCase;
        this.optimizeProductImagesUseCase = optimizeProductImagesUseCase;
        this.optimizeCategoryImagesUseCase = optimizeCategoryImagesUseCase;
        this.productRepository = productRepository;
        this.imageOptimizer = imageOptimizer;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @PostMapping("/migrate-category-images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrateCategoryImagesUseCase.Result> migrateCategories() {
        return ResponseEntity.ok(migrateUseCase.execute());
    }

    @PostMapping("/optimize-products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptimizeProductImagesUseCase.Result> optimizeProducts() {
        return ResponseEntity.ok(optimizeProductImagesUseCase.execute());
    }

    @PostMapping("/optimize-categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptimizeCategoryImagesUseCase.Result> optimizeCategories() {
        return ResponseEntity.ok(optimizeCategoryImagesUseCase.execute());
    }

    @PostMapping("/optimize-existing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptimizeResult> optimizeExisting() throws IOException {
        return ResponseEntity.ok(walkAndReencode(false));
    }

    @PostMapping("/optimize-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptimizeAllResult> optimizeAll() throws IOException {
        OptimizeProductImagesUseCase.Result products = safeRunProducts();
        OptimizeCategoryImagesUseCase.Result categories = safeRunCategories();
        OptimizeResult others = walkAndReencode(true);
        return ResponseEntity.ok(new OptimizeAllResult(products, categories, others));
    }

    @GetMapping("/hero-models")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HeroModelsResponse> getHeroModels() {
        return ResponseEntity.ok(currentHeroModels());
    }

    @PostMapping(value = "/hero-models/{slot}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HeroModelResponse> uploadHeroModel(
            @PathVariable String slot,
            @RequestParam("file") MultipartFile file) {
        String normalizedSlot = normalizeSlot(slot);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe adjuntar una imagen");
        }

        try {
            byte[] normalizedPng = toPng(file.getBytes());
            HeroModelResponse saved = saveHeroModel(normalizedSlot, normalizedPng);
            return ResponseEntity.ok(saved);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen enviada");
        }
    }

    @PostMapping("/hero-models/{slot}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HeroModelResponse> assignHeroModel(
            @PathVariable String slot,
            @RequestBody AssignHeroModelRequest request) {
        String normalizedSlot = normalizeSlot(slot);

        if (request == null || (request.productId() == null && !StringUtils.hasText(request.imageUrl()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes enviar productId o imageUrl para asignar la imagen");
        }

        String sourceImageUrl = resolveSourceImageUrl(request);
        ImageSource source = loadSourceImage(sourceImageUrl);
        byte[] normalizedPng = toPng(source.bytes());
        HeroModelResponse saved = saveHeroModel(normalizedSlot, normalizedPng);
        return ResponseEntity.ok(saved);
    }

    private OptimizeProductImagesUseCase.Result safeRunProducts() {
        try {
            return optimizeProductImagesUseCase.execute();
        } catch (RuntimeException e) {
            return new OptimizeProductImagesUseCase.Result(0, 0, 0, 1, 0L, java.util.List.of(e.getMessage()));
        }
    }

    private OptimizeCategoryImagesUseCase.Result safeRunCategories() {
        try {
            return optimizeCategoryImagesUseCase.execute();
        } catch (RuntimeException e) {
            return new OptimizeCategoryImagesUseCase.Result(0, 0, 0, 1, 0L, java.util.List.of(e.getMessage()));
        }
    }

    /**
     * Walks media root recursively and re-encodes any JPEG file in place.
     * When skipKnownFolders=true, products/ and categories/ are excluded
     * because their dedicated use cases already covered them (and updated DB rows).
     */
    private OptimizeResult walkAndReencode(boolean skipKnownFolders) throws IOException {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicLong bytesSaved = new AtomicLong();

        if (!Files.exists(mediaRoot)) {
            return new OptimizeResult(0, 0, 0, 0);
        }

        try (var stream = Files.walk(mediaRoot)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                if (skipKnownFolders) {
                    Path rel = mediaRoot.relativize(file);
                    if (rel.getNameCount() > 0 && SKIP_FOLDERS.contains(rel.getName(0).toString())) {
                        skipped.incrementAndGet();
                        return;
                    }
                }
                String name = file.getFileName().toString().toLowerCase();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
                if (!JPEG_EXTENSIONS.contains(ext)) {
                    skipped.incrementAndGet();
                    return;
                }
                try {
                    byte[] original = Files.readAllBytes(file);
                    byte[] optimized = imageOptimizer.reencodeJpeg(original);
                    if (optimized.length < original.length) {
                        bytesSaved.addAndGet(original.length - optimized.length);
                        Files.write(file, optimized);
                    }
                    processed.incrementAndGet();
                } catch (IOException e) {
                    failed.incrementAndGet();
                }
            });
        }

        return new OptimizeResult(processed.get(), skipped.get(), failed.get(), bytesSaved.get());
    }

    private HeroModelsResponse currentHeroModels() {
        long leftUpdatedAt = resolveLastModified(HERO_LEFT_FILENAME);
        long rightUpdatedAt = resolveLastModified(HERO_RIGHT_FILENAME);
        return new HeroModelsResponse(
                new HeroModelResponse(LEFT_SLOT, heroSlotUrl(LEFT_SLOT), leftUpdatedAt),
                new HeroModelResponse(RIGHT_SLOT, heroSlotUrl(RIGHT_SLOT), rightUpdatedAt));
    }

    private HeroModelResponse saveHeroModel(String slot, byte[] pngBytes) {
        String filename = heroSlotFilename(slot);
        Path folder = mediaRoot.resolve(HERO_MODELS_FOLDER).normalize();
        if (!folder.startsWith(mediaRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de almacenamiento invalida");
        }
        Path target = folder.resolve(filename).normalize();
        if (!target.startsWith(mediaRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de almacenamiento invalida");
        }
        try {
            Files.createDirectories(folder);
            Files.write(target, pngBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            long updatedAt = Files.getLastModifiedTime(target).toMillis();
            return new HeroModelResponse(slot, heroSlotUrl(slot), updatedAt);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo guardar la imagen del hero");
        }
    }

    private String resolveSourceImageUrl(AssignHeroModelRequest request) {
        if (request.productId() != null) {
            var product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            if (!StringUtils.hasText(product.getImageUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto no tiene imagen principal");
            }
            return product.getImageUrl().trim();
        }
        return request.imageUrl().trim();
    }

    private ImageSource loadSourceImage(String imageUrl) {
        if (imageUrl.startsWith("/api/media/")) {
            Path relative = Paths.get(imageUrl.substring("/api/media/".length())).normalize();
            Path file = mediaRoot.resolve(relative).normalize();
            if (!file.startsWith(mediaRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de imagen invalida");
            }
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro la imagen origen");
            }
            ensureAllowedImagePath(file.getFileName().toString());
            try {
                return new ImageSource(Files.readAllBytes(file));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen origen");
            }
        }

        URI sourceUri;
        try {
            sourceUri = URI.create(imageUrl);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de imagen invalida");
        }
        if (!"http".equalsIgnoreCase(sourceUri.getScheme()) && !"https".equalsIgnoreCase(sourceUri.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten URLs http(s)");
        }
        ensureAllowedImagePath(sourceUri.getPath());

        HttpRequest request = HttpRequest.newBuilder(sourceUri)
                .GET()
                .timeout(Duration.ofSeconds(12))
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No se pudo descargar imagen origen (status " + response.statusCode() + ")");
            }
            return new ImageSource(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al descargar imagen origen");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al descargar imagen origen");
        }
    }

    private byte[] toPng(byte[] rawBytes) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no soportado");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean written = ImageIO.write(source, "png", out);
            if (!written) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo convertir la imagen a PNG");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar la imagen");
        }
    }

    private long resolveLastModified(String filename) {
        Path file = mediaRoot.resolve(HERO_MODELS_FOLDER).resolve(filename).normalize();
        if (!file.startsWith(mediaRoot)) {
            return 0L;
        }
        try {
            if (!Files.exists(file)) {
                return 0L;
            }
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String normalizeSlot(String slot) {
        if (!StringUtils.hasText(slot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot de modelo no valido");
        }
        String normalized = slot.trim().toLowerCase();
        if (!LEFT_SLOT.equals(normalized) && !RIGHT_SLOT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot de modelo no valido");
        }
        return normalized;
    }

    private String heroSlotFilename(String slot) {
        return LEFT_SLOT.equals(slot) ? HERO_LEFT_FILENAME : HERO_RIGHT_FILENAME;
    }

    private String heroSlotUrl(String slot) {
        return "/api/media/" + HERO_MODELS_FOLDER + "/" + heroSlotFilename(slot);
    }

    private void ensureAllowedImagePath(String pathOrFilename) {
        String lower = pathOrFilename == null ? "" : pathOrFilename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";
        if (!SOURCE_IMAGE_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Extension de imagen no permitida");
        }
    }

    public record OptimizeResult(int processed, int skipped, int failed, long bytesSaved) {}
    public record OptimizeAllResult(
            OptimizeProductImagesUseCase.Result products,
            OptimizeCategoryImagesUseCase.Result categories,
            OptimizeResult others) {}
    public record HeroModelResponse(String slot, String url, long updatedAt) {}
    public record HeroModelsResponse(HeroModelResponse left, HeroModelResponse right) {}
    public record AssignHeroModelRequest(UUID productId, String imageUrl) {}
    private record ImageSource(byte[] bytes) {}
}
