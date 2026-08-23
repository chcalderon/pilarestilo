package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.infrastructure.persistence.entities.ProductEntity;
import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
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
 * can be verified against it. Mirrors OptimizeCategoryImagesUseCaseTest, whose sibling use case
 * this class shares its shape with.
 */
class OptimizeProductImagesUseCaseTest {

    @TempDir
    Path mediaRoot;

    ProductJpaRepository productRepo;
    ImageOptimizerService imageOptimizer;
    OptimizeProductImagesUseCase useCase;
    Path productsDir;

    @BeforeEach
    void setUp() throws IOException {
        productRepo = mock(ProductJpaRepository.class);
        imageOptimizer = mock(ImageOptimizerService.class);
        useCase = new OptimizeProductImagesUseCase(productRepo, imageOptimizer, mediaRoot.toString());
        productsDir = mediaRoot.resolve("products");
        Files.createDirectories(productsDir);
        lenient().when(productRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void aMissingProductsFolderIsAnEmptyResultNotAnError() throws IOException {
        Files.delete(productsDir);

        var result = useCase.execute();

        assertThat(result.processed()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void skipsFormatsTheOptimizerDoesNotHandle() throws IOException {
        Files.write(productsDir.resolve("banner.webp"), "fake-webp".getBytes());

        var result = useCase.execute();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.processed()).isZero();
    }

    @Test
    void skipsAnExtensionItDoesNotRecognizeAtAll() throws IOException {
        Files.write(productsDir.resolve("readme.txt"), "not an image".getBytes());

        var result = useCase.execute();

        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void rewritesAJpegOnlyWhenTheOptimizerActuallyShrinksIt() throws IOException {
        byte[] original = "original-jpeg-bytes-are-longer".getBytes();
        byte[] smaller = "shorter".getBytes();
        Files.write(productsDir.resolve("hero.jpg"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn(smaller);

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.bytesSaved()).isEqualTo(original.length - smaller.length);
        assertThat(Files.readAllBytes(productsDir.resolve("hero.jpg"))).isEqualTo(smaller);
    }

    @Test
    void countsAJpegAsProcessedButLeavesItUntouchedWhenReencodingDoesNotShrinkIt() throws IOException {
        byte[] original = "abc".getBytes();
        Files.write(productsDir.resolve("hero.jpg"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn("abcdef".getBytes());

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.bytesSaved()).isZero();
        assertThat(Files.readAllBytes(productsDir.resolve("hero.jpg"))).isEqualTo(original);
    }

    @Test
    void convertsAPngToJpegAndUpdatesTheProductThatReferencedTheOldFilename() throws IOException {
        byte[] original = "png-bytes".getBytes();
        byte[] jpeg = "jpeg".getBytes();
        Files.write(productsDir.resolve("hero.png"), original);
        when(imageOptimizer.reencodeJpeg(original)).thenReturn(jpeg);

        ProductEntity product = new ProductEntity();
        product.setId(UUID.randomUUID());
        product.setImageUrl("/api/media/products/hero.png");
        when(productRepo.findAll()).thenReturn(List.of(product));

        var result = useCase.execute();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.renamed()).isEqualTo(1);
        assertThat(Files.exists(productsDir.resolve("hero.png"))).isFalse();
        assertThat(Files.readAllBytes(productsDir.resolve("hero.jpg"))).isEqualTo(jpeg);
        assertThat(product.getImageUrl()).isEqualTo("/api/media/products/hero.jpg");
        verify(productRepo).saveAll(List.of(product));
    }

    @Test
    void convertingAPngWithNoProductReferencingItStillRenamesTheFile() throws IOException {
        byte[] original = "png-bytes".getBytes();
        Files.write(productsDir.resolve("orphan.png"), original);
        when(imageOptimizer.reencodeJpeg(any())).thenReturn("jpeg".getBytes());

        var result = useCase.execute();

        assertThat(result.renamed()).isEqualTo(1);
        assertThat(Files.exists(productsDir.resolve("orphan.jpg"))).isTrue();
        verify(productRepo, never()).saveAll(any());
    }
}
