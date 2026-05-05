package com.pilarestilo.shared.infrastructure.web.controllers;

import com.pilarestilo.category.application.usecases.MigrateCategoryImagesUseCase;
import com.pilarestilo.category.application.usecases.OptimizeCategoryImagesUseCase;
import com.pilarestilo.product.application.usecases.OptimizeProductImagesUseCase;
import com.pilarestilo.shared.infrastructure.services.ImageOptimizerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/admin/media")
public class MediaAdminController {

    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> SKIP_FOLDERS = Set.of("products", "categories");

    private final MigrateCategoryImagesUseCase migrateUseCase;
    private final OptimizeProductImagesUseCase optimizeProductImagesUseCase;
    private final OptimizeCategoryImagesUseCase optimizeCategoryImagesUseCase;
    private final ImageOptimizerService imageOptimizer;
    private final Path mediaRoot;

    public MediaAdminController(
            MigrateCategoryImagesUseCase migrateUseCase,
            OptimizeProductImagesUseCase optimizeProductImagesUseCase,
            OptimizeCategoryImagesUseCase optimizeCategoryImagesUseCase,
            ImageOptimizerService imageOptimizer,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.migrateUseCase = migrateUseCase;
        this.optimizeProductImagesUseCase = optimizeProductImagesUseCase;
        this.optimizeCategoryImagesUseCase = optimizeCategoryImagesUseCase;
        this.imageOptimizer = imageOptimizer;
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

    public record OptimizeResult(int processed, int skipped, int failed, long bytesSaved) {}
    public record OptimizeAllResult(
            OptimizeProductImagesUseCase.Result products,
            OptimizeCategoryImagesUseCase.Result categories,
            OptimizeResult others) {}
}
