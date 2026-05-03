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
            return new Result(0, 0, 0, 0, List.of());
        }

        int processed = 0, renamed = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try (var stream = Files.list(productsDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String filename = file.getFileName().toString();
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                        : "";

                if (SKIP_EXTENSIONS.contains(ext)) {
                    skipped++;
                    continue;
                }

                try {
                    byte[] original = Files.readAllBytes(file);

                    if (ext.equals("jpg") || ext.equals("jpeg")) {
                        byte[] optimized = imageOptimizer.reencodeJpeg(original);
                        if (optimized.length < original.length) {
                            Files.write(file, optimized);
                        }
                        processed++;

                    } else if (ext.equals("png")) {
                        byte[] jpegBytes = imageOptimizer.reencodeJpeg(original);
                        String newFilename = filename.substring(0, filename.lastIndexOf('.')) + ".jpg";
                        Path newFile = productsDir.resolve(newFilename);

                        Files.write(newFile, jpegBytes);
                        Files.delete(file);

                        String oldUrl = "/api/media/products/" + filename;
                        String newUrl = "/api/media/products/" + newFilename;

                        int updated = updateProductImageUrl(oldUrl, newUrl);
                        if (updated > 0) {
                            log.info("Renamed {} → {} (updated {} product records)", filename, newFilename, updated);
                        } else {
                            log.warn("Renamed file {} → {} but no product referenced it", filename, newFilename);
                        }
                        processed++;
                        renamed++;
                    } else {
                        skipped++;
                    }

                } catch (IOException e) {
                    failed++;
                    errors.add(filename + ": " + e.getMessage());
                    log.warn("Failed to optimize {}: {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not list products media directory", e);
        }

        return new Result(processed, renamed, skipped, failed, errors);
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

    public record Result(int processed, int renamed, int skipped, int failed, List<String> errors) {}
}
