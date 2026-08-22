package com.pilarestilo.productai.infrastructure.ai;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ProductAiNodeBridgeClient {

    private final MediaStorageService mediaStorageService;
    private final Path mediaRoot;

    @Value("${app.product-ai.image.web-width:1024}")
    private int webWidth;

    @Value("${app.product-ai.image.web-height:1280}")
    private int webHeight;

    @Value("${app.product-ai.image.web-jpeg-quality:0.88}")
    private float webJpegQuality;

    @Value("${app.product-ai.image.thumb-width:320}")
    private int thumbWidth;

    @Value("${app.product-ai.image.thumb-height:400}")
    private int thumbHeight;

    @Value("${app.product-ai.image.thumb-jpeg-quality:0.82}")
    private float thumbJpegQuality;

    public ProductAiNodeBridgeClient(
            MediaStorageService mediaStorageService,
            @Value("${app.media.storage-path:./media}") String mediaStoragePath
    ) {
        this.mediaStorageService = mediaStorageService;
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    public StoredDerivatives storeDerivatives(String folder, String prefix, byte[] masterBytes) {
        byte[] webBytes = toJpeg(masterBytes, Math.max(webWidth, 1), Math.max(webHeight, 1), clampQuality(webJpegQuality));
        byte[] thumbBytes = toJpeg(masterBytes, Math.max(thumbWidth, 1), Math.max(thumbHeight, 1), clampQuality(thumbJpegQuality));
        String masterUrl = storeBytes(folder, prefix + "-master.png", "image/png", masterBytes);
        String webUrl = storeBytes(folder, prefix + "-web.jpg", "image/jpeg", webBytes);
        String thumbUrl = storeBytes(folder, prefix + "-thumb.jpg", "image/jpeg", thumbBytes);
        return new StoredDerivatives(masterUrl, webUrl, thumbUrl);
    }

    public byte[] loadOriginalAssetBytes(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new DomainException("Asset URL is empty");
        }
        try {
            if (originalUrl.startsWith("/api/media/")) {
                String relative = originalUrl.substring("/api/media/".length());
                Path target = mediaRoot.resolve(relative).normalize();
                if (!target.startsWith(mediaRoot)) {
                    throw new DomainException("Invalid local media path");
                }
                return Files.readAllBytes(target);
            }
            URL url = URI.create(originalUrl).toURL();
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ex) {
            throw new DomainException("Could not read source image: " + ex.getMessage());
        }
    }

    private String storeBytes(String folder, String filename, String contentType, byte[] data) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            return mediaStorageService.storeRaw(in, folder, filename, contentType);
        } catch (IOException ex) {
            throw new DomainException("Could not store processed AI image: " + ex.getMessage());
        }
    }

    private byte[] toJpeg(byte[] sourceBytes, int targetWidth, int targetHeight, float quality) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        } catch (IOException _) {
            throw new DomainException("Could not decode generated image for JPEG resize");
        }
        if (source == null) {
            throw new DomainException("Generated image could not be decoded");
        }
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.min(targetWidth / (double) source.getWidth(), targetHeight / (double) source.getHeight());
            int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (targetWidth - drawW) / 2;
            int y = (targetHeight - drawH) / 2;
            graphics.drawImage(source, x, y, drawW, drawH, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(canvas, null, null), params);
        } catch (IOException ex) {
            throw new DomainException("Could not encode JPEG derivative: " + ex.getMessage());
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private float clampQuality(float quality) {
        if (Float.isNaN(quality)) {
            return 0.85f;
        }
        if (quality < 0.1f) {
            return 0.1f;
        }
        return Math.min(quality, 1.0f);
    }

    public record StoredDerivatives(
            String masterUrl,
            String webUrl,
            String thumbUrl
    ) {
    }
}
