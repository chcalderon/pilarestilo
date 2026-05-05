package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
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
public class OptimizeCategoryImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(OptimizeCategoryImagesUseCase.class);
    private static final Set<String> SKIP_EXTENSIONS = Set.of("gif", "webp", "avif");

    private final CategoryJpaRepository categoryRepo;
    private final ImageOptimizerService imageOptimizer;
    private final Path mediaRoot;

    public OptimizeCategoryImagesUseCase(
            CategoryJpaRepository categoryRepo,
            ImageOptimizerService imageOptimizer,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.categoryRepo = categoryRepo;
        this.imageOptimizer = imageOptimizer;
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @Transactional
    public Result execute() {
        Path categoriesDir = mediaRoot.resolve("categories");
        if (!Files.exists(categoriesDir)) {
            return new Result(0, 0, 0, 0, 0, List.of());
        }

        int processed = 0, renamed = 0, skipped = 0, failed = 0;
        long bytesSaved = 0L;
        List<String> errors = new ArrayList<>();

        try (var stream = Files.list(categoriesDir)) {
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
                            bytesSaved += (original.length - optimized.length);
                            Files.write(file, optimized);
                        }
                        processed++;

                    } else if (ext.equals("png")) {
                        byte[] jpegBytes = imageOptimizer.reencodeJpeg(original);
                        String newFilename = filename.substring(0, filename.lastIndexOf('.')) + ".jpg";
                        Path newFile = categoriesDir.resolve(newFilename);

                        Files.write(newFile, jpegBytes);
                        Files.delete(file);
                        bytesSaved += Math.max(0, original.length - jpegBytes.length);

                        String oldUrl = "/api/media/categories/" + filename;
                        String newUrl = "/api/media/categories/" + newFilename;

                        int updated = updateCategoryImageUrl(oldUrl, newUrl);
                        if (updated > 0) {
                            log.info("Renamed {} -> {} (updated {} category records)", filename, newFilename, updated);
                        } else {
                            log.warn("Renamed file {} -> {} but no category referenced it", filename, newFilename);
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
            throw new RuntimeException("Could not list categories media directory", e);
        }

        return new Result(processed, renamed, skipped, failed, bytesSaved, errors);
    }

    private int updateCategoryImageUrl(String oldUrl, String newUrl) {
        var matches = categoryRepo.findAll().stream()
                .filter(c -> oldUrl.equals(c.getImageUrl()))
                .toList();
        matches.forEach(c -> c.setImageUrl(newUrl));
        if (!matches.isEmpty()) {
            categoryRepo.saveAll(matches);
        }
        return matches.size();
    }

    public record Result(int processed, int renamed, int skipped, int failed, long bytesSaved, List<String> errors) {}
}
