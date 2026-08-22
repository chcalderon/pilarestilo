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
        int migrated = 0, failed = 0;
        List<MigrationError> errors = new ArrayList<>();

        for (Category cat : categories) {
            String imageUrl = cat.getImageUrl();
            if (imageUrl == null || imageUrl.startsWith("/api/media/")) continue;

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
                migrated++;
                log.info("Migrated category {} image: {} → {}", cat.getId(), imageUrl, storedUrl);

            } catch (InterruptedException _) {
                // Migrating hundreds of images is exactly the loop somebody stops halfway.
                Thread.currentThread().interrupt();
                failed++;
                errors.add(new MigrationError(cat.getId(), imageUrl, "interrupted"));
                break;
            } catch (Exception e) {
                failed++;
                errors.add(new MigrationError(cat.getId(), imageUrl, e.getMessage()));
                log.warn("Failed to migrate category {} image {}: {}", cat.getId(), imageUrl, e.getMessage());
            }
        }
        return new Result(migrated, failed, errors);
    }

    public record MigrationError(UUID categoryId, String originalUrl, String reason) {}
    public record Result(int migrated, int failed, List<MigrationError> errors) {}
}
