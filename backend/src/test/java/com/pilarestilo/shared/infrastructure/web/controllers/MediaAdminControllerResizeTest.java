package com.pilarestilo.shared.infrastructure.web.controllers;

import com.pilarestilo.category.application.usecases.MigrateCategoryImagesUseCase;
import com.pilarestilo.category.application.usecases.OptimizeCategoryImagesUseCase;
import com.pilarestilo.product.application.usecases.OptimizeProductImagesUseCase;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.infrastructure.services.ImageOptimizerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Characterization tests written before reducing processResizeFolder's Cognitive Complexity
 * (S3776) -- it had none. A real temp folder with real (tiny) images pins the current, correct
 * behaviour so the refactor can be verified against it.
 */
class MediaAdminControllerResizeTest {

    @TempDir
    Path mediaRoot;

    MediaAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new MediaAdminController(
                mock(MigrateCategoryImagesUseCase.class),
                mock(OptimizeProductImagesUseCase.class),
                mock(OptimizeCategoryImagesUseCase.class),
                mock(ProductRepository.class),
                mock(ImageOptimizerService.class),
                mediaRoot.toString());
    }

    @Test
    void resizesAnImageLargerThanTheTarget() throws IOException {
        writeImage(mediaRoot.resolve("products/big.jpg"), 2000, 2000, "jpg");

        var result = controller.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.resized()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        BufferedImage after = ImageIO.read(mediaRoot.resolve("products/big.jpg").toFile());
        assertThat(Math.max(after.getWidth(), after.getHeight())).isEqualTo(1772);
    }

    @Test
    void leavesAnImageSmallerThanTheTargetUntouched() throws IOException {
        writeImage(mediaRoot.resolve("categories/small.png"), 100, 100, "png");

        var result = controller.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.resized()).isZero();
        BufferedImage after = ImageIO.read(mediaRoot.resolve("categories/small.png").toFile());
        assertThat(after.getWidth()).isEqualTo(100);
    }

    @Test
    void skipsAFileWhoseExtensionIsNotAnImageFormat() throws IOException {
        Path file = mediaRoot.resolve("products/notes.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not an image");

        var result = controller.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.processed()).isZero();
    }

    @Test
    void countsAnUnreadableFileWithAnImageExtensionAsFailed() throws IOException {
        Path file = mediaRoot.resolve("products/corrupt.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "this is not really a jpeg");

        var result = controller.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.resized()).isZero();
    }

    @Test
    void aFolderOutsideProductsAndCategoriesIsNeverTouched() throws IOException {
        writeImage(mediaRoot.resolve("hero-models/left.jpg"), 2000, 2000, "jpg");

        var result = controller.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.processed()).isZero();
        assertThat(result.resized()).isZero();
    }

    @Test
    void aMissingMediaRootIsAnEmptyResultNotAnError() throws IOException {
        Path missing = mediaRoot.resolve("does-not-exist");
        MediaAdminController withMissingRoot = new MediaAdminController(
                mock(MigrateCategoryImagesUseCase.class),
                mock(OptimizeProductImagesUseCase.class),
                mock(OptimizeCategoryImagesUseCase.class),
                mock(ProductRepository.class),
                mock(ImageOptimizerService.class),
                missing.toString());

        var result = withMissingRoot.resizeProductsAndCategoriesTo15cm().getBody();

        assertThat(result.processed()).isZero();
        assertThat(result.failed()).isZero();
    }

    private void writeImage(Path path, int width, int height, String format) throws IOException {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(width, height,
                "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        Files.write(path, out.toByteArray());
    }
}
