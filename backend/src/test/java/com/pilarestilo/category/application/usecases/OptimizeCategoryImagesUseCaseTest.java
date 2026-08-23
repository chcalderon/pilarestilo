package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.shared.infrastructure.services.ImageOptimizerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests written before reducing execute()'s Cognitive Complexity (S3776) -- it
 * had none. Real files in a temp media root pin the current, correct behaviour so the refactor
 * can be verified against it.
 */
class OptimizeCategoryImagesUseCaseTest {

    @TempDir
    Path mediaRoot;

    CategoryJpaRepository categoryRepo;
    ImageOptimizerService imageOptimizer;
    OptimizeCategoryImagesUseCase useCase;
    Path categoriesDir;

    @BeforeEach
    void setUp() throws IOException {
        categoryRepo = mock(CategoryJpaRepository.class);
        imageOptimizer = mock(ImageOptimizerService.class);
        useCase = new OptimizeCategoryImagesUseCase(categoryRepo, imageOptimizer, mediaRoot.toString());
        categoriesDir = mediaRoot.resolve("categories");
        Files.createDirectories(categoriesDir);
        lenient().when(categoryRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void aMissingCategoriesFolderIsAnEmptyResultNotAnError() throws IOException {
        Files.delete(categoriesDir);

        var result = useCase.execute();

        assertThat(result.processed()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void skipsFormatsTheOptimizerDoesNotHandle() throws IOException {
        Files.write(categoriesDir.resolve("banner.webp"), "fake-webp".getBytes());

        var result = useCase.execute();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.processed()).isZero();
    }

    @Test
    void skipsAnExtensionItDoesNotRecognizeAtAll() throws IOException {
        Files.write(categoriesDir.resolve("readme.txt"), "not an image".getBytes());

        var result = useCase.execute();

        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void rewritesAJpegOnlyWhenTheOptimizerActuallyShrinksIt() throws IOException {
        byte[] original = "original-jpeg-bytes-are-longer".getBytes();
        byte[] smaller = "shorter".getBytes();
        Files.write(categoriesDir.resolve("hero.jpg"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn(smaller);

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.bytesSaved()).isEqualTo(original.length - smaller.length);
        assertThat(Files.readAllBytes(categoriesDir.resolve("hero.jpg"))).isEqualTo(smaller);
    }

    @Test
    void countsAJpegAsProcessedButLeavesItUntouchedWhenReencodingDoesNotShrinkIt() throws IOException {
        byte[] original = "abc".getBytes();
        Files.write(categoriesDir.resolve("hero.jpg"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn("abcdef".getBytes());

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.bytesSaved()).isZero();
        assertThat(Files.readAllBytes(categoriesDir.resolve("hero.jpg"))).isEqualTo(original);
    }

    @Test
    void convertsAPngToJpegAndUpdatesTheCategoryThatReferencedTheOldFilename() throws IOException {
        byte[] original = "png-bytes".getBytes();
        byte[] jpeg = "jpeg".getBytes();
        Files.write(categoriesDir.resolve("hero.png"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn(jpeg);

        CategoryEntity category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setImageUrl("/api/media/categories/hero.png");
        when(categoryRepo.findAll()).thenReturn(List.of(category));

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.renamed()).isEqualTo(1);
        assertThat(Files.exists(categoriesDir.resolve("hero.png"))).isFalse();
        assertThat(Files.readAllBytes(categoriesDir.resolve("hero.jpg"))).isEqualTo(jpeg);
        assertThat(category.getImageUrl()).isEqualTo("/api/media/categories/hero.jpg");
        verify(categoryRepo).saveAll(List.of(category));
    }

    @Test
    void convertingAPngWithNoCategoryReferencingItStillRenamesTheFile() throws IOException {
        byte[] original = "png-bytes".getBytes();
        Files.write(categoriesDir.resolve("orphan.png"), original);
        when(imageOptimizer.reencodeJpeg(any())).thenReturn("jpeg".getBytes());

        var result = useCase.execute();

        assertThat(result.renamed()).isEqualTo(1);
        assertThat(Files.exists(categoriesDir.resolve("orphan.jpg"))).isTrue();
        verify(categoryRepo, never()).saveAll(any());
    }
}
