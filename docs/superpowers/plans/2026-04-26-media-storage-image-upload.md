# Media Storage Abstraction & Image Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded filesystem writes with a pluggable `MediaStoragePort` (LOCAL + S3_COMPATIBLE), add drag & drop image upload to categories and products, migrate existing category Unsplash images to local storage, and remove "Ruta activa" labels.

**Architecture:** `MediaStoragePort` interface with `LocalFileStorageAdapter` and `S3StorageAdapter` as beans; `MediaStorageService` reads `SystemSettings.mediaStorageProvider()` at runtime and delegates to the correct adapter. `MediaUploadController` is refactored to use `MediaStorageService`. A one-time `MigrateCategoryImagesUseCase` downloads external URLs to local storage. The frontend gets a reusable `ImageDropzone` island.

**Tech Stack:** Spring Boot 3 / Java 17 / hexagonal architecture; AWS SDK v2 (`software.amazon.awssdk:s3`); React + TypeScript in Astro islands; existing `POST /api/media/upload` endpoint (contract unchanged).

---

## File Map

### New backend files
```
backend/src/main/java/com/pilarestilo/shared/domain/ports/MediaStoragePort.java
backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapter.java
backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/S3StorageAdapter.java
backend/src/main/java/com/pilarestilo/shared/infrastructure/services/MediaStorageService.java
backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaAdminController.java
backend/src/main/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCase.java
```

### Modified backend files
```
backend/pom.xml                                                     (add S3 SDK dependency)
backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaUploadController.java
```

### New test files
```
backend/src/test/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapterTest.java
backend/src/test/java/com/pilarestilo/shared/infrastructure/services/MediaStorageServiceTest.java
backend/src/test/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCaseTest.java
```

### New frontend files
```
frontend/src/islands/admin/ImageDropzone.tsx
```

### Modified frontend files
```
frontend/src/lib/api.ts                                  (add uploadMediaFile, migrateCategoryImages)
frontend/src/islands/admin/CategoryTree.tsx              (replace URL input with ImageDropzone)
frontend/src/islands/admin/ProductForm.tsx               (replace upload section, remove Ruta activa)
frontend/src/islands/admin/SystemSettingsPanel.tsx       (add migration button)
```

---

## Task 1: AWS S3 SDK dependency

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add S3 SDK dependency**

In `backend/pom.xml`, inside `<dependencies>`, add after the last `spring-boot-starter-*` block:

```xml
<!-- AWS SDK v2 S3 (S3-compatible storage) -->
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
  <version>2.25.60</version>
</dependency>
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>url-connection-client</artifactId>
  <version>2.25.60</version>
</dependency>
```

- [ ] **Step 2: Verify dependency resolves**

```bash
cd backend && mvn dependency:resolve -q 2>&1 | grep -E "ERROR|software.amazon"
```

Expected: no ERROR lines, `software.amazon.awssdk:s3:jar:2.25.60` listed.

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "build: add AWS SDK v2 S3 dependency for S3-compatible storage"
```

---

## Task 2: MediaStoragePort + LocalFileStorageAdapter

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/domain/ports/MediaStoragePort.java`
- Create: `backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapterTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.pilarestilo.shared.infrastructure.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageAdapterTest {

    @Test
    void storesFileAndReturnsPublicUrl(@TempDir Path tempDir) throws Exception {
        var adapter = new LocalFileStorageAdapter(tempDir.toString());
        var data = new ByteArrayInputStream("imagedata".getBytes());

        String url = adapter.store(data, "products", "test.jpg", "image/jpeg");

        assertEquals("/api/media/products/test.jpg", url);
        assertTrue(Files.exists(tempDir.resolve("products/test.jpg")));
        assertArrayEquals("imagedata".getBytes(), Files.readAllBytes(tempDir.resolve("products/test.jpg")));
    }

    @Test
    void createsFolderIfNotExists(@TempDir Path tempDir) {
        var adapter = new LocalFileStorageAdapter(tempDir.toString());
        var data = new ByteArrayInputStream(new byte[]{1, 2, 3});

        assertDoesNotThrow(() -> adapter.store(data, "categories", "cat.png", "image/png"));
        assertTrue(Files.isDirectory(tempDir.resolve("categories")));
    }

    @Test
    void deleteRemovesFile(@TempDir Path tempDir) throws Exception {
        var adapter = new LocalFileStorageAdapter(tempDir.toString());
        Path file = tempDir.resolve("products/img.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "data");

        adapter.delete("products", "img.jpg");

        assertFalse(Files.exists(file));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd backend && mvn test -pl . -Dtest=LocalFileStorageAdapterTest -q 2>&1 | tail -5
```

Expected: FAIL — class not found.

- [ ] **Step 3: Create MediaStoragePort interface**

```java
// backend/src/main/java/com/pilarestilo/shared/domain/ports/MediaStoragePort.java
package com.pilarestilo.shared.domain.ports;

import java.io.InputStream;

public interface MediaStoragePort {
    /**
     * Store data from stream and return the public URL.
     */
    String store(InputStream data, String folder, String filename, String contentType);

    void delete(String folder, String filename);
}
```

- [ ] **Step 4: Create LocalFileStorageAdapter**

```java
// backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapter.java
package com.pilarestilo.shared.infrastructure.adapters;

import com.pilarestilo.shared.domain.ports.MediaStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class LocalFileStorageAdapter implements MediaStoragePort {

    private final Path mediaRoot;

    public LocalFileStorageAdapter(@Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @Override
    public String store(InputStream data, String folder, String filename, String contentType) {
        Path targetDir = mediaRoot.resolve(folder).normalize();
        if (!targetDir.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Invalid folder: " + folder);
        }
        Path target = targetDir.resolve(filename).normalize();
        try {
            Files.createDirectories(targetDir);
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file locally", e);
        }
        return "/api/media/" + folder + "/" + filename;
    }

    @Override
    public void delete(String folder, String filename) {
        Path target = mediaRoot.resolve(folder).resolve(filename).normalize();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // log and continue — deletion failure is non-critical
        }
    }
}
```

- [ ] **Step 5: Run test to confirm it passes**

```bash
cd backend && mvn test -pl . -Dtest=LocalFileStorageAdapterTest -q 2>&1 | tail -5
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/domain/ports/MediaStoragePort.java \
        backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapter.java \
        backend/src/test/java/com/pilarestilo/shared/infrastructure/adapters/LocalFileStorageAdapterTest.java
git commit -m "feat(media): MediaStoragePort interface and LocalFileStorageAdapter"
```

---

## Task 3: S3StorageAdapter

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/S3StorageAdapter.java`

No unit test for S3 (requires live bucket or container — integration test for future). The service-level test in Task 4 verifies the adapter is selected correctly.

- [ ] **Step 1: Read SystemSettings to find S3 getter names**

```bash
grep -n "mediaS3\|s3Bucket\|s3Endpoint\|s3Region\|s3AccessKey\|s3SecretKey\|s3PathStyle\|s3PublicBase\|decrypt\|cryptoSecret" \
  backend/src/main/java/com/pilarestilo/systemsettings/domain/model/SystemSettings.java | head -30
```

Note the exact method names — use them in the adapter below.

- [ ] **Step 2: Find the crypto/decrypt utility**

```bash
grep -rn "decrypt\|CryptoUtil\|cryptoSecret\|SYSTEM_SETTINGS_CRYPTO" \
  backend/src/main/java/com/pilarestilo/systemsettings/ | grep -v ".class" | head -20
```

Note how the existing code decrypts the secret key (likely `CryptoUtil.decrypt(encrypted, cryptoSecret)` or similar). Use the same pattern in S3StorageAdapter.

- [ ] **Step 3: Create S3StorageAdapter**

Adapt the exact method names from Step 1 and the decrypt pattern from Step 2:

```java
// backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/S3StorageAdapter.java
package com.pilarestilo.shared.infrastructure.adapters;

import com.pilarestilo.shared.domain.ports.MediaStoragePort;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Component
public class S3StorageAdapter implements MediaStoragePort {

    private final SystemSettingsRepository settingsRepo;
    private final String cryptoSecret;

    public S3StorageAdapter(
            SystemSettingsRepository settingsRepo,
            @Value("${system.settings.crypto.secret:}") String cryptoSecret) {
        this.settingsRepo = settingsRepo;
        this.cryptoSecret = cryptoSecret;
    }

    @Override
    public String store(InputStream data, String folder, String filename, String contentType) {
        var settings = settingsRepo.get();
        validateConfigured(settings);

        String key = folder + "/" + filename;
        try {
            byte[] bytes = data.readAllBytes();
            buildClient(settings).putObject(
                PutObjectRequest.builder()
                    .bucket(settings.mediaS3Bucket())
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (IOException e) {
            throw new RuntimeException("Could not read file for S3 upload", e);
        }

        String base = settings.mediaS3PublicBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + key;
    }

    @Override
    public void delete(String folder, String filename) {
        var settings = settingsRepo.get();
        if (!isConfigured(settings)) return;
        buildClient(settings).deleteObject(
            DeleteObjectRequest.builder()
                .bucket(settings.mediaS3Bucket())
                .key(folder + "/" + filename)
                .build()
        );
    }

    private void validateConfigured(com.pilarestilo.systemsettings.domain.model.SystemSettings s) {
        if (!isConfigured(s)) {
            throw new IllegalStateException(
                "S3 storage is not configured. Set endpoint, bucket, accessKeyId and secretKey in System Settings.");
        }
    }

    private boolean isConfigured(com.pilarestilo.systemsettings.domain.model.SystemSettings s) {
        return s.mediaS3Bucket() != null && !s.mediaS3Bucket().isBlank()
            && s.mediaS3AccessKeyId() != null && !s.mediaS3AccessKeyId().isBlank();
    }

    private S3Client buildClient(com.pilarestilo.systemsettings.domain.model.SystemSettings s) {
        // Decrypt secret key using the same pattern as other encrypted settings in the codebase
        // (check SystemSettingsRepositoryAdapter for the decrypt call — adapt if different)
        String secretKey = decryptSecret(s.mediaS3SecretKeyEncrypted());

        var builder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s.mediaS3AccessKeyId(), secretKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(s.mediaS3PathStyleEnabled())
                .build());

        if (s.mediaS3Endpoint() != null && !s.mediaS3Endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s.mediaS3Endpoint()));
        }
        if (s.mediaS3Region() != null && !s.mediaS3Region().isBlank()) {
            builder.region(Region.of(s.mediaS3Region()));
        } else {
            builder.region(Region.US_EAST_1); // fallback for MinIO
        }

        return builder.build();
    }

    private String decryptSecret(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return "";
        // Adapt this line to match how other encrypted fields are decrypted in this codebase.
        // Look at SystemSettingsRepositoryAdapter or CryptoUtil for the exact method.
        // Example pattern: return CryptoUtil.decrypt(encrypted, cryptoSecret);
        return encrypted; // REPLACE with actual decrypt call after Step 2 above
    }
}
```

**IMPORTANT:** After creating this file, run Step 2 above again to find the exact decrypt call and replace the `decryptSecret` body with it.

- [ ] **Step 4: Verify compilation**

```bash
cd backend && mvn compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/infrastructure/adapters/S3StorageAdapter.java
git commit -m "feat(media): S3StorageAdapter for S3-compatible object storage"
```

---

## Task 4: MediaStorageService

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/infrastructure/services/MediaStorageService.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/infrastructure/services/MediaStorageServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.pilarestilo.shared.infrastructure.services;

import com.pilarestilo.shared.infrastructure.adapters.LocalFileStorageAdapter;
import com.pilarestilo.shared.infrastructure.adapters.S3StorageAdapter;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock LocalFileStorageAdapter localAdapter;
    @Mock S3StorageAdapter s3Adapter;
    @Mock SystemSettingsRepository settingsRepo;
    @InjectMocks MediaStorageService service;

    @Test
    void usesLocalAdapterWhenProviderIsLocal() throws Exception {
        var settings = mock(SystemSettings.class);
        when(settings.mediaStorageProvider())
            .thenReturn(com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider.LOCAL);
        when(settingsRepo.get()).thenReturn(settings);
        when(localAdapter.store(any(), anyString(), anyString(), anyString()))
            .thenReturn("/api/media/products/test.jpg");

        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        service.store(file, "products");

        verify(localAdapter).store(any(), eq("products"), anyString(), eq("image/jpeg"));
        verifyNoInteractions(s3Adapter);
    }

    @Test
    void usesS3AdapterWhenProviderIsS3Compatible() throws Exception {
        var settings = mock(SystemSettings.class);
        when(settings.mediaStorageProvider())
            .thenReturn(com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider.S3_COMPATIBLE);
        when(settingsRepo.get()).thenReturn(settings);
        when(s3Adapter.store(any(), anyString(), anyString(), anyString()))
            .thenReturn("https://bucket.example.com/products/test.jpg");

        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        service.store(file, "products");

        verify(s3Adapter).store(any(), eq("products"), anyString(), eq("image/jpeg"));
        verifyNoInteractions(localAdapter);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd backend && mvn test -pl . -Dtest=MediaStorageServiceTest -q 2>&1 | tail -5
```

Expected: FAIL — class not found.

- [ ] **Step 3: Create MediaStorageService**

Extract all filename-generation private methods from `MediaUploadController` into this service (they will be deleted from the controller in Task 5):

```java
// backend/src/main/java/com/pilarestilo/shared/infrastructure/services/MediaStorageService.java
package com.pilarestilo.shared.infrastructure.services;

import com.pilarestilo.shared.domain.ports.MediaStoragePort;
import com.pilarestilo.shared.infrastructure.adapters.LocalFileStorageAdapter;
import com.pilarestilo.shared.infrastructure.adapters.S3StorageAdapter;
import com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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

    public MediaStorageService(LocalFileStorageAdapter localAdapter,
                               S3StorageAdapter s3Adapter,
                               SystemSettingsRepository settingsRepo) {
        this.localAdapter = localAdapter;
        this.s3Adapter = s3Adapter;
        this.settingsRepo = settingsRepo;
    }

    /** Upload a MultipartFile — generates filename automatically. */
    public String store(MultipartFile file, String folder) {
        String extension = resolveExtension(file);
        String baseName = sanitizeBaseName(extractBaseName(file.getOriginalFilename()));
        String filename = buildFilename(baseName, extension);
        try {
            return activeAdapter().store(file.getInputStream(), folder, filename, file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Could not read uploaded file", e);
        }
    }

    /** Store raw stream with explicit filename — used by migration use case. */
    public String storeRaw(InputStream data, String folder, String filename, String contentType) {
        return activeAdapter().store(data, folder, filename, contentType);
    }

    private MediaStoragePort activeAdapter() {
        var provider = settingsRepo.get().mediaStorageProvider();
        return provider == MediaStorageProvider.S3_COMPATIBLE ? s3Adapter : localAdapter;
    }

    // ── Filename helpers (extracted from MediaUploadController) ──────────────

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
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd backend && mvn test -pl . -Dtest=MediaStorageServiceTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/infrastructure/services/MediaStorageService.java \
        backend/src/test/java/com/pilarestilo/shared/infrastructure/services/MediaStorageServiceTest.java
git commit -m "feat(media): MediaStorageService with runtime LOCAL/S3 delegation"
```

---

## Task 5: Refactor MediaUploadController

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaUploadController.java`

- [ ] **Step 1: Replace MediaUploadController with refactored version**

Replace the entire file content:

```java
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

    @PostMapping("/upload-proof")
    @PreAuthorize("isAuthenticated()")
    public MediaUploadResponse uploadProof(@RequestParam("file") MultipartFile file) {
        return storeFile(file, "payment-proofs");
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
```

- [ ] **Step 2: Run full test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, same test count as before.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaUploadController.java
git commit -m "refactor(media): MediaUploadController delegates to MediaStorageService"
```

---

## Task 6: MigrateCategoryImagesUseCase + MediaAdminController

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaAdminController.java`
- Test: `backend/src/test/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCaseTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrateCategoryImagesUseCaseTest {

    @Mock CategoryRepository categoryRepository;
    @Mock MediaStorageService mediaStorageService;
    @InjectMocks MigrateCategoryImagesUseCase useCase;

    @Test
    void skipsAlreadyMigratedCategories() {
        var cat = Category.create("ropa", "Ropa", "Clothing", null, 0, "/api/media/categories/ropa.jpg");
        cat.setId(UUID.randomUUID());
        when(categoryRepository.findAll()).thenReturn(List.of(cat));

        var result = useCase.execute();

        assertEquals(0, result.migrated());
        assertEquals(0, result.failed());
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void skipsNullImageUrl() {
        var cat = Category.create("ropa", "Ropa", "Clothing", null, 0, null);
        cat.setId(UUID.randomUUID());
        when(categoryRepository.findAll()).thenReturn(List.of(cat));

        var result = useCase.execute();

        assertEquals(0, result.migrated());
        verifyNoInteractions(mediaStorageService);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd backend && mvn test -pl . -Dtest=MigrateCategoryImagesUseCaseTest -q 2>&1 | tail -5
```

Expected: FAIL — class not found.

- [ ] **Step 3: Create MigrateCategoryImagesUseCase**

```java
// backend/src/main/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCase.java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
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
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

                String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
                String ext = mediaStorageService.extensionFromContentType(contentType);
                if (ext.isBlank()) ext = "jpg";
                String filename = "category-" + cat.getId() + "." + ext;

                String storedUrl;
                try (InputStream body = response.body()) {
                    storedUrl = mediaStorageService.storeRaw(body, "categories", filename, contentType);
                }

                cat.update(cat.getSlug(), cat.getNameEs(), cat.getNameEn(),
                    cat.getParentId(), cat.getSortOrder(), cat.isActive(), storedUrl);
                categoryRepository.save(cat);
                migrated++;
                log.info("Migrated category {} image: {} → {}", cat.getId(), imageUrl, storedUrl);

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
```

- [ ] **Step 4: Create MediaAdminController**

```java
// backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaAdminController.java
package com.pilarestilo.shared.infrastructure.web.controllers;

import com.pilarestilo.category.application.usecases.MigrateCategoryImagesUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/media")
public class MediaAdminController {

    private final MigrateCategoryImagesUseCase migrateUseCase;

    public MediaAdminController(MigrateCategoryImagesUseCase migrateUseCase) {
        this.migrateUseCase = migrateUseCase;
    }

    @PostMapping("/migrate-category-images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrateCategoryImagesUseCase.Result> migrateCategories() {
        return ResponseEntity.ok(migrateUseCase.execute());
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn test -pl . -Dtest=MigrateCategoryImagesUseCaseTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run full suite**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCase.java \
        backend/src/main/java/com/pilarestilo/shared/infrastructure/web/controllers/MediaAdminController.java \
        backend/src/test/java/com/pilarestilo/category/application/usecases/MigrateCategoryImagesUseCaseTest.java
git commit -m "feat(media): MigrateCategoryImagesUseCase and MediaAdminController"
```

---

## Task 7: Frontend — uploadMediaFile in api.ts + ImageDropzone component

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/islands/admin/ImageDropzone.tsx`

- [ ] **Step 1: Add uploadMediaFile and migrateCategoryImages to api.ts**

At the end of `frontend/src/lib/api.ts`, add:

```typescript
export async function uploadMediaFile(file: File, folder: string, token: string): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  form.append('folder', folder);
  const res = await fetch(`${API_BASE}/media/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Upload failed (${res.status})`);
  }
  const data = await res.json() as MediaUploadDto;
  return data.url;
}

export async function migrateCategoryImages(token: string): Promise<{
  migrated: number;
  failed: number;
  errors: { categoryId: string; originalUrl: string; reason: string }[];
}> {
  return apiFetch('/admin/media/migrate-category-images', {
    method: 'POST',
    headers: authHeaders(token),
  });
}
```

- [ ] **Step 2: Create ImageDropzone.tsx**

```tsx
// frontend/src/islands/admin/ImageDropzone.tsx
import { useState, useRef } from 'react';
import { Upload, ImagePlus, Loader2 } from 'lucide-react';
import { uploadMediaFile } from '../../lib/api';

interface Props {
  value?: string;
  onUpload: (url: string) => void;
  folder: string;
  token: string;
  label?: string;
}

type State = 'idle' | 'dragging' | 'uploading' | 'error';

export default function ImageDropzone({ value, onUpload, folder, token, label }: Props) {
  const [state, setState] = useState<State>('idle');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = async (file: File) => {
    setState('uploading');
    setError('');
    try {
      const url = await uploadMediaFile(file, folder, token);
      setPreview(url);
      onUpload(url);
      setState('idle');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al subir imagen');
      setState('error');
    }
  };

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setState('idle');
    const file = e.dataTransfer.files[0];
    if (file) void upload(file);
  };

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void upload(file);
    e.currentTarget.value = '';
  };

  const dragging = state === 'dragging';
  const uploading = state === 'uploading';

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <span className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">
          {label}
        </span>
      )}
      <div
        onClick={() => !uploading && inputRef.current?.click()}
        onDragEnter={e => { e.preventDefault(); setState('dragging'); }}
        onDragOver={e => { e.preventDefault(); setState('dragging'); }}
        onDragLeave={() => setState('idle')}
        onDrop={onDrop}
        className={`relative cursor-pointer border-2 border-dashed transition-colors select-none
          ${dragging ? 'border-pe-rose bg-pe-rose/5' : 'border-pe-black/15 hover:border-pe-rose/40'}
          ${state === 'error' ? 'border-red-400' : ''}
        `}
        style={{ minHeight: '96px' }}
      >
        {preview ? (
          <div className="relative w-full" style={{ minHeight: '96px' }}>
            <img
              src={preview}
              alt="Vista previa"
              className="w-full h-24 object-cover"
              loading="lazy"
            />
            <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 hover:opacity-100 transition-opacity">
              {uploading ? (
                <Loader2 size={20} className="text-white animate-spin" />
              ) : (
                <span className="font-sans text-[0.62rem] uppercase tracking-wider text-white flex items-center gap-1">
                  <Upload size={12} /> Cambiar
                </span>
              )}
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center gap-2 p-6 text-pe-charcoal/40">
            {uploading ? (
              <Loader2 size={22} className="animate-spin text-pe-rose" />
            ) : (
              <>
                <ImagePlus size={22} />
                <span className="font-sans text-[0.68rem] text-center">
                  {dragging ? 'Soltá para subir' : 'Arrastrá o hacé clic para subir'}
                </span>
              </>
            )}
          </div>
        )}
      </div>
      {state === 'error' && (
        <p className="font-sans text-[0.65rem] text-red-500">{error}</p>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onChange}
      />
    </div>
  );
}
```

- [ ] **Step 3: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep -E "ImageDropzone|api.ts" | head -10
```

Expected: no errors for the new files.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/islands/admin/ImageDropzone.tsx
git commit -m "feat(frontend): ImageDropzone component and uploadMediaFile API function"
```

---

## Task 8: CategoryTree — replace URL input with ImageDropzone

**Files:**
- Modify: `frontend/src/islands/admin/CategoryTree.tsx`

- [ ] **Step 1: Read current token usage in CategoryTree.tsx**

```bash
grep -n "token\|useAuthStore\|readAuthTokenCookie" frontend/src/islands/admin/CategoryTree.tsx | head -10
```

Note how the token is obtained (likely `useAuthStore` + `readAuthTokenCookie`).

- [ ] **Step 2: Add ImageDropzone import to CategoryTree.tsx**

At the top of the file, after existing imports, add:

```tsx
import ImageDropzone from './ImageDropzone';
```

- [ ] **Step 3: Replace the URL imagen input in FormRow**

In `FormRow` component inside `CategoryTree.tsx`, find and replace the entire `"URL imagen"` field block:

```tsx
// REMOVE this block:
<div className="flex flex-col gap-0.5 sm:col-span-2 lg:col-span-3">
  <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">URL imagen</label>
  <input className={INPUT_CLASS} value={form.imageUrl}
    onChange={e => setForm(f => ({ ...f, imageUrl: e.target.value }))} placeholder="https://…" />
</div>

// REPLACE with:
<div className="flex flex-col gap-0.5 sm:col-span-2 lg:col-span-3">
  <ImageDropzone
    label="Imagen"
    folder="categories"
    value={form.imageUrl || undefined}
    onUpload={url => setForm(f => ({ ...f, imageUrl: url }))}
    token={token}
  />
</div>
```

**Note:** `FormRow` receives a `token` prop. If it doesn't already have `token` in its props interface, add `token: string` to `FormRowProps` and pass `token={token}` wherever `<FormRow>` is used in `CategoryTree.tsx`.

- [ ] **Step 4: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep CategoryTree | head -10
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/admin/CategoryTree.tsx
git commit -m "feat(admin): replace category URL input with ImageDropzone"
```

---

## Task 9: ProductForm — ImageDropzone + remove Ruta activa

**Files:**
- Modify: `frontend/src/islands/admin/ProductForm.tsx`

- [ ] **Step 1: Read current image upload section in ProductForm.tsx**

```bash
sed -n '460,530p' frontend/src/islands/admin/ProductForm.tsx
```

Confirm the exact lines to replace (should match what was read during planning: lines ~470–522).

- [ ] **Step 2: Add ImageDropzone import**

At the top of `ProductForm.tsx`, after existing imports:

```tsx
import ImageDropzone from './ImageDropzone';
```

- [ ] **Step 3: Replace the image upload section**

Find the entire `<div>` block starting with `<label className={labelClass}>Imagen del producto</label>` and containing the current upload button + "Ruta activa" + file input. Replace it with:

```tsx
<div>
  <label className={labelClass}>Imagen del producto</label>
  <ImageDropzone
    folder="products"
    value={previewUrl || undefined}
    onUpload={url => {
      setPreviewUrl(url);
      setForm(prev => ({ ...prev, imageUrl: url }));
    }}
    token={token}
  />
</div>
```

**Also remove:**
- The `fileInputRef` declaration (if only used for the old upload button)
- The `uploadingImage` state and `handleImageUpload` function (if only used for the old upload button)
- Any `Upload`, `ImagePlus` lucide imports that are now unused

**Check:** Keep `previewUrl` state — `ImageDropzone` reads it via `value` prop.

- [ ] **Step 4: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep ProductForm | head -10
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/admin/ProductForm.tsx
git commit -m "feat(admin): replace product image upload with ImageDropzone, remove Ruta activa"
```

---

## Task 10: SystemSettingsPanel — migration button

**Files:**
- Modify: `frontend/src/islands/admin/SystemSettingsPanel.tsx`

- [ ] **Step 1: Find the media storage section in SystemSettingsPanel.tsx**

```bash
grep -n "media\|Media\|S3\|storage\|migrat" frontend/src/islands/admin/SystemSettingsPanel.tsx | head -20
```

Note the line numbers of the media storage settings section.

- [ ] **Step 2: Add migrateCategoryImages import to api imports**

Find the existing import from `../../lib/api` in `SystemSettingsPanel.tsx` and add `migrateCategoryImages` to it.

- [ ] **Step 3: Add migration state**

In the component body, after existing `useState` declarations, add:

```tsx
const [migrating, setMigrating] = useState(false);
const [migrateResult, setMigrateResult] = useState<{ migrated: number; failed: number } | null>(null);
```

- [ ] **Step 4: Add migration handler**

After existing handler functions, add:

```tsx
const handleMigrateCategories = async () => {
  if (!token) return;
  setMigrating(true);
  setMigrateResult(null);
  try {
    const result = await migrateCategoryImages(token);
    setMigrateResult({ migrated: result.migrated, failed: result.failed });
  } catch {
    setMigrateResult({ migrated: 0, failed: -1 });
  } finally {
    setMigrating(false);
  }
};
```

- [ ] **Step 5: Add migration button UI**

In the media storage section of the JSX (near the S3 settings), add after the last media settings field:

```tsx
<div className="pt-4 border-t border-pe-black/8">
  <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45 mb-2">
    Migración de imágenes
  </p>
  <p className="font-sans text-[0.72rem] text-pe-charcoal/60 mb-3">
    Descarga las imágenes de categorías desde URLs externas al almacenamiento configurado.
    Solo procesa imágenes que aún no estén almacenadas localmente.
  </p>
  <button
    type="button"
    onClick={handleMigrateCategories}
    disabled={migrating}
    className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
  >
    {migrating ? <Loader2 size={13} className="animate-spin" /> : null}
    {migrating ? 'Migrando...' : 'Migrar imágenes de categorías'}
  </button>
  {migrateResult && (
    <p className={`font-sans text-[0.72rem] mt-2 ${migrateResult.failed === -1 ? 'text-red-500' : 'text-pe-charcoal/60'}`}>
      {migrateResult.failed === -1
        ? 'Error al ejecutar la migración.'
        : `Migradas: ${migrateResult.migrated} · Fallidas: ${migrateResult.failed}`}
    </p>
  )}
</div>
```

Add `Loader2` to the lucide-react import if not already present.

- [ ] **Step 6: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep SystemSettings | head -10
```

Expected: no errors.

- [ ] **Step 7: Run full backend test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/SystemSettingsPanel.tsx
git commit -m "feat(admin): category image migration button in SystemSettingsPanel"
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Task |
|---|---|
| `MediaStoragePort` interface | Task 2 |
| `LocalFileStorageAdapter` extracts filesystem logic | Tasks 2 + 5 |
| `S3StorageAdapter` with runtime credentials | Task 3 |
| `MediaStorageService` delegates based on `SystemSettings.mediaStorageProvider()` at runtime | Task 4 |
| `MediaUploadController` refactored to use service | Task 5 |
| `MediaResourceConfig` unchanged | ✅ no task needed — confirmed in design |
| Category image migration endpoint + one-time use case | Task 6 |
| Migration button in `SystemSettingsPanel` | Task 10 |
| `uploadMediaFile` API function | Task 7 |
| `migrateCategoryImages` API function | Task 7 |
| `ImageDropzone` with drag & drop + click | Task 7 |
| `CategoryTree`: replace URL input | Task 8 |
| `ProductForm`: replace upload section | Task 9 |
| Remove "Ruta activa" label | Task 9 |

All spec requirements covered.
