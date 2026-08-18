package com.pilarestilo.shared.infrastructure.web.controllers;

import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/media")
public class MediaUploadController {

    private static final Pattern FOLDER_PATTERN = Pattern.compile("^[a-z0-9/_-]+$");

    private final MediaStorageService mediaStorageService;

    public MediaUploadController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public MediaUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "products") String folder) {
        return storeFile(file, normalizeFolder(folder));
    }

    private MediaUploadResponse storeFile(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        String url = mediaStorageService.store(file, folder);
        return new MediaUploadResponse(url, extractFilename(url), file.getSize());
    }

    private String normalizeFolder(String folder) {
        String raw = StringUtils.hasText(folder) ? folder.trim().toLowerCase(Locale.ROOT) : "products";
        String normalized = raw.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank()) normalized = "products";
        if (!FOLDER_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media folder");
        }
        return normalized;
    }

    private String extractFilename(String url) {
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    public record MediaUploadResponse(String url, String filename, long size) {}
}
