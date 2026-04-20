package com.pilarestilo.shared.infrastructure.web.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/media")
public class MediaUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "avif");
    private static final Pattern FOLDER_PATTERN = Pattern.compile("^[a-z0-9/_-]+$");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final Path mediaRoot;

    public MediaUploadController(@Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public MediaUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "products") String folder
    ) {
        return storeFile(file, folder);
    }

    @PostMapping("/upload-proof")
    @PreAuthorize("isAuthenticated()")
    public MediaUploadResponse uploadProof(
            @RequestParam("file") MultipartFile file
    ) {
        return storeFile(file, "payment-proofs");
    }

    private MediaUploadResponse storeFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        String extension = resolveExtension(file);
        String normalizedFolder = normalizeFolder(folder);
        Path targetDir = resolveTargetDir(normalizedFolder);
        String originalBaseName = extractBaseName(file.getOriginalFilename());
        String safeBaseName = sanitizeBaseName(originalBaseName);
        String filename = buildFilename(safeBaseName, extension);
        Path targetFile = targetDir.resolve(filename).normalize();

        if (!targetFile.startsWith(targetDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target file path");
        }

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store image", ex);
        }

        String publicUrl = "/api/media/" + normalizedFolder + "/" + filename;
        return new MediaUploadResponse(publicUrl, filename, file.getSize());
    }

    private String resolveExtension(MultipartFile file) {
        String candidate = extensionFromFilename(file.getOriginalFilename());
        if (candidate.isBlank()) {
            candidate = extensionFromContentType(file.getContentType());
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(lower)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format");
        }
        if ("jpeg".equals(lower)) {
            return "jpg";
        }
        return lower;
    }

    private String extensionFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1);
    }

    private String extensionFromContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "image/avif" -> "avif";
            default -> "";
        };
    }

    private String normalizeFolder(String folder) {
        String raw = StringUtils.hasText(folder) ? folder.trim().toLowerCase(Locale.ROOT) : "products";
        String normalized = raw.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            normalized = "products";
        }
        if (!FOLDER_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media folder");
        }
        return normalized;
    }

    private Path resolveTargetDir(String normalizedFolder) {
        Path targetDir = mediaRoot.resolve(normalizedFolder).normalize();
        if (!targetDir.startsWith(mediaRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media folder path");
        }
        return targetDir;
    }

    private String extractBaseName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "image";
        }
        String clean = Paths.get(originalFilename).getFileName().toString();
        int idx = clean.lastIndexOf('.');
        if (idx <= 0) {
            return clean;
        }
        return clean.substring(0, idx);
    }

    private String sanitizeBaseName(String baseName) {
        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM.matcher(normalized).replaceAll("-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            return "image";
        }
        return slug.length() > 48 ? slug.substring(0, 48) : slug;
    }

    private String buildFilename(String baseName, String extension) {
        long now = Instant.now().toEpochMilli();
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return now + "-" + shortId + "-" + baseName + "." + extension;
    }

    public record MediaUploadResponse(String url, String filename, long size) {}
}
