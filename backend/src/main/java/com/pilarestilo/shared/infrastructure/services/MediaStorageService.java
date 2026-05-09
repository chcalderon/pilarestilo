package com.pilarestilo.shared.infrastructure.services;

import com.pilarestilo.shared.domain.ports.MediaStoragePort;
import com.pilarestilo.shared.infrastructure.adapters.LocalFileStorageAdapter;
import com.pilarestilo.shared.infrastructure.adapters.S3StorageAdapter;
import com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "avif");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final LocalFileStorageAdapter localAdapter;
    private final S3StorageAdapter s3Adapter;
    private final SystemSettingsRepository settingsRepo;
    private final ImageOptimizerService imageOptimizer;

    public MediaStorageService(LocalFileStorageAdapter localAdapter,
                               S3StorageAdapter s3Adapter,
                               SystemSettingsRepository settingsRepo,
                               ImageOptimizerService imageOptimizer) {
        this.localAdapter = localAdapter;
        this.s3Adapter = s3Adapter;
        this.settingsRepo = settingsRepo;
        this.imageOptimizer = imageOptimizer;
    }

    public String store(MultipartFile file, String folder) {
        try {
            byte[] raw = file.getBytes();
            ImageOptimizerService.OptimizedImage optimized = optimizeByFolder(raw, file.getContentType(), folder);
            String baseName = sanitizeBaseName(extractBaseName(file.getOriginalFilename()));
            String filename = buildFilename(baseName, optimized.extension());
            return activeAdapter().store(new ByteArrayInputStream(optimized.data()), folder, filename, "image/" + optimized.extension());
        } catch (IOException e) {
            throw new RuntimeException("Could not process uploaded file", e);
        }
    }

    public String storeOptimizedBytes(byte[] raw, String contentType, String folder, String baseFilename) {
        try {
            ImageOptimizerService.OptimizedImage optimized = optimizeByFolder(raw, contentType, folder);
            String baseName = sanitizeBaseName(extractBaseName(baseFilename));
            String filename = buildFilename(baseName, optimized.extension());
            return activeAdapter().store(new ByteArrayInputStream(optimized.data()), folder, filename, "image/" + optimized.extension());
        } catch (IOException e) {
            throw new RuntimeException("Could not process raw bytes", e);
        }
    }

    public String storeRaw(InputStream data, String folder, String filename, String contentType) {
        return activeAdapter().store(data, folder, filename, contentType);
    }

    private MediaStoragePort activeAdapter() {
        var provider = settingsRepo.get().getMediaStorageProvider();
        return provider == MediaStorageProvider.S3_COMPATIBLE ? s3Adapter : localAdapter;
    }

    private ImageOptimizerService.OptimizedImage optimizeByFolder(byte[] raw, String contentType, String folder) throws IOException {
        if (isProductsOrCategoriesFolder(folder)) {
            return imageOptimizer.optimizeForProductsAndCategories(raw, contentType);
        }
        return imageOptimizer.optimize(raw, contentType);
    }

    private boolean isProductsOrCategoriesFolder(String folder) {
        if (!StringUtils.hasText(folder)) return false;
        String normalized = folder.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        return normalized.equals("products")
                || normalized.startsWith("products/")
                || normalized.equals("categories")
                || normalized.startsWith("categories/");
    }

    public String resolveExtension(MultipartFile file) {
        String candidate = extensionFromFilename(file.getOriginalFilename());
        if (candidate.isBlank()) candidate = extensionFromContentType(file.getContentType());
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(lower)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported image format");
        }
        return "jpeg".equals(lower) ? "jpg" : lower;
    }

    public String extensionFromContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) return "";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            case "image/gif"  -> "gif";
            case "image/avif" -> "avif";
            default -> "";
        };
    }

    private String extensionFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1);
    }

    private String extractBaseName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) return "image";
        String clean = java.nio.file.Paths.get(originalFilename).getFileName().toString();
        int idx = clean.lastIndexOf('.');
        return idx <= 0 ? clean : clean.substring(0, idx);
    }

    private String sanitizeBaseName(String baseName) {
        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM.matcher(normalized).replaceAll("-").replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) return "image";
        return slug.length() > 48 ? slug.substring(0, 48) : slug;
    }

    private String buildFilename(String baseName, String extension) {
        return Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8)
            + "-" + baseName + "." + extension;
    }
}
