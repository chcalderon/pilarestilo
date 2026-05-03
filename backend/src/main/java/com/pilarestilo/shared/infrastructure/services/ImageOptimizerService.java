package com.pilarestilo.shared.infrastructure.services;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

@Service
public class ImageOptimizerService {

    private static final int MAX_DIMENSION = 1800;
    private static final float JPEG_QUALITY = 0.90f;
    private static final Set<String> PASSTHROUGH_TYPES = Set.of("image/gif", "image/webp", "image/avif");

    public record OptimizedImage(byte[] data, String extension) {}

    /**
     * Converts an uploaded image to an optimized JPEG, preserving aspect ratio.
     * GIF, WebP, and AVIF are passed through unchanged.
     *
     * @param imageBytes  raw bytes from the upload
     * @param contentType MIME type of the uploaded file
     * @return optimized bytes + "jpg" extension, or original bytes + original extension for passthrough types
     */
    public OptimizedImage optimize(byte[] imageBytes, String contentType) throws IOException {
        String lct = contentType == null ? "" : contentType.toLowerCase();
        if (PASSTHROUGH_TYPES.contains(lct)) {
            String ext = switch (lct) {
                case "image/webp" -> "webp";
                case "image/avif" -> "avif";
                default -> "gif";
            };
            return new OptimizedImage(imageBytes, ext);
        }

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (source == null) {
            return new OptimizedImage(imageBytes, "jpg");
        }

        int w = source.getWidth();
        int h = source.getHeight();

        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            double scale = Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h);
            w = Math.max(1, (int) Math.round(w * scale));
            h = Math.max(1, (int) Math.round(h * scale));
        }

        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }

        byte[] jpegBytes = encodeJpeg(canvas, JPEG_QUALITY);
        return new OptimizedImage(jpegBytes, "jpg");
    }

    /**
     * Re-encodes an existing JPEG file at the standard quality/size.
     * Used for batch optimization of already-stored images.
     */
    public byte[] reencodeJpeg(byte[] imageBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (source == null) return imageBytes;

        int w = source.getWidth();
        int h = source.getHeight();

        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            double scale = Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h);
            w = Math.max(1, (int) Math.round(w * scale));
            h = Math.max(1, (int) Math.round(h * scale));
        }

        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }

        return encodeJpeg(canvas, JPEG_QUALITY);
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
