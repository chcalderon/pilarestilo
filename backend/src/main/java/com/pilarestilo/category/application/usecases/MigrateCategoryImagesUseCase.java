package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MigrateCategoryImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(MigrateCategoryImagesUseCase.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private final CategoryRepository categoryRepository;
    private final MediaStorageService mediaStorageService;

    public MigrateCategoryImagesUseCase(CategoryRepository categoryRepository,
                                        MediaStorageService mediaStorageService) {
        this.categoryRepository = categoryRepository;
        this.mediaStorageService = mediaStorageService;
    }

    @Transactional
    public Result execute() {
        List<Category> categories = categoryRepository.findAll();
        AtomicInteger migrated = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<MigrationError> errors = new ArrayList<>();

        for (Category cat : categories) {
            if (migrateOne(cat, migrated, failed, errors)) {
                // Migrating hundreds of images is exactly the loop somebody stops halfway.
                break;
            }
        }
        return new Result(migrated.get(), failed.get(), errors);
    }

    /** @return true if the calling thread was interrupted and the caller should stop the loop. */
    private boolean migrateOne(Category cat, AtomicInteger migrated, AtomicInteger failed, List<MigrationError> errors) {
        String imageUrl = cat.getImageUrl();
        if (imageUrl == null || imageUrl.startsWith("/api/media/")) {
            return false;
        }

        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());

            String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
            String baseFilename = "category-" + cat.getId();

            String storedUrl = mediaStorageService.storeOptimizedBytes(
                response.body(), contentType, "categories", baseFilename);

            cat.update(
                cat.getSlug(),
                cat.getNameEs(),
                cat.getNameEn(),
                cat.getParentId(),
                cat.getSortOrder(),
                cat.isActive(),
                cat.isFeatured(),
                storedUrl
            );
            categoryRepository.save(cat);
            migrated.incrementAndGet();
            log.info("Migrated category {} image: {} → {}", cat.getId(), imageUrl, storedUrl);
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            failed.incrementAndGet();
            errors.add(new MigrationError(cat.getId(), imageUrl, "interrupted"));
            return true;
        } catch (Exception e) {
            failed.incrementAndGet();
            errors.add(new MigrationError(cat.getId(), imageUrl, e.getMessage()));
            log.warn("Failed to migrate category {} image {}: {}", cat.getId(), imageUrl, e.getMessage());
            return false;
        }
    }

    public record MigrationError(UUID categoryId, String originalUrl, String reason) {}
    public record Result(int migrated, int failed, List<MigrationError> errors) {}
}
