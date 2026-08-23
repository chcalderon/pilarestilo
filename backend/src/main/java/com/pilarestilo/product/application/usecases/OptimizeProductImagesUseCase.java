package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import com.pilarestilo.shared.infrastructure.services.ImageOptimizerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OptimizeProductImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(OptimizeProductImagesUseCase.class);
    private static final Set<String> SKIP_EXTENSIONS = Set.of("gif", "webp", "avif");

    private final ProductJpaRepository productRepo;
    private final ImageOptimizerService imageOptimizer;
    private final Path mediaRoot;

    public OptimizeProductImagesUseCase(
            ProductJpaRepository productRepo,
            ImageOptimizerService imageOptimizer,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.productRepo = productRepo;
        this.imageOptimizer = imageOptimizer;
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @Transactional
    public Result execute() {
        Path productsDir = mediaRoot.resolve("products");
        if (!Files.exists(productsDir)) {
            return new Result(0, 0, 0, 0, 0L, List.of());
        }

        Counters counters = new Counters();
        try (var stream = Files.list(productsDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                processFile(file, productsDir, counters);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not list products media directory", e);
        }

        return new Result(counters.processed.get(), counters.renamed.get(), counters.skipped.get(),
                counters.failed.get(), counters.bytesSaved.get(), counters.errors);
    }

    /** Mutable per-run tally, threaded through the per-file processing that {@link #execute()} loops over. */
    private static final class Counters {
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger renamed = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicLong bytesSaved = new AtomicLong();
        final List<String> errors = new ArrayList<>();
    }

    private void processFile(Path file, Path productsDir, Counters counters) {
        String filename = file.getFileName().toString();
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";

        if (SKIP_EXTENSIONS.contains(ext)) {
            counters.skipped.incrementAndGet();
            return;
        }

        try {
            byte[] original = Files.readAllBytes(file);
            switch (ext) {
                case "jpg", "jpeg" -> reencodeInPlace(file, original, counters);
                case "png" -> convertPngToJpeg(file, filename, productsDir, original, counters);
                default -> counters.skipped.incrementAndGet();
            }
        } catch (IOException e) {
            counters.failed.incrementAndGet();
            counters.errors.add(filename + ": " + e.getMessage());
            log.warn("Failed to optimize {}: {}", filename, e.getMessage());
        }
    }

    private void reencodeInPlace(Path file, byte[] original, Counters counters) throws IOException {
        byte[] optimized = imageOptimizer.reencodeJpeg(original);
        if (optimized.length < original.length) {
            counters.bytesSaved.addAndGet((long) original.length - optimized.length);
            Files.write(file, optimized);
        }
        counters.processed.incrementAndGet();
    }

    private void convertPngToJpeg(Path file, String filename, Path productsDir, byte[] original, Counters counters)
            throws IOException {
        byte[] jpegBytes = imageOptimizer.reencodeJpeg(original);
        String newFilename = filename.substring(0, filename.lastIndexOf('.')) + ".jpg";
        Path newFile = productsDir.resolve(newFilename);

        Files.write(newFile, jpegBytes);
        Files.delete(file);
        counters.bytesSaved.addAndGet(Math.max(0, original.length - jpegBytes.length));

        String oldUrl = "/api/media/products/" + filename;
        String newUrl = "/api/media/products/" + newFilename;
        int updated = updateProductImageUrl(oldUrl, newUrl);
        if (updated > 0) {
            log.info("Renamed {} → {} (updated {} product records)", filename, newFilename, updated);
        } else {
            log.warn("Renamed file {} → {} but no product referenced it", filename, newFilename);
        }
        counters.processed.incrementAndGet();
        counters.renamed.incrementAndGet();
    }

    private int updateProductImageUrl(String oldUrl, String newUrl) {
        var matches = productRepo.findAll().stream()
                .filter(p -> oldUrl.equals(p.getImageUrl()))
                .toList();
        matches.forEach(p -> p.setImageUrl(newUrl));
        if (!matches.isEmpty()) {
            productRepo.saveAll(matches);
        }
        return matches.size();
    }

    public record Result(int processed, int renamed, int skipped, int failed, long bytesSaved, List<String> errors) {}
}
