package com.pilarestilo.billing.infrastructure.storage;

import com.pilarestilo.shared.infrastructure.storage.StoredFileType;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Private storage for boleta files.
 *
 * <p>Deliberately <strong>not</strong> {@code MediaStorageService}. That writes under
 * {@code app.media.storage-path}, and {@code MediaResourceConfig} maps the whole of that root to
 * {@code /api/media/**}, which {@code SecurityConfig} declares {@code permitAll}. A boleta carries a
 * RUT, a buyer name and amounts; putting it there would publish personal data to anyone holding the
 * url, weeks before Ley 21.719 comes into force. Its second problem is that
 * {@code MediaStorageService.store} runs every upload through the image optimiser, which turns a PDF
 * into a file named {@code .jpg} containing the original bytes.
 *
 * <p>So: a separate root, never served statically, read back only through the authenticated
 * endpoint on {@code SalesDocumentController}.
 */
@Component
public class SalesDocumentFileStorage {

    private static final Set<String> ALLOWED_EXTENSIONS = StoredFileType.allowedExtensions();
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public SalesDocumentFileStorage(@Value("${app.documents.storage-path:./documents}") String storagePath) {
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    /** @return an opaque relative path stored on the document row; never a public url. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("The document file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("The document file exceeds 10 MB");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new DomainException("Invalid document filename");
        }
        try (InputStream data = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DomainException("Could not store the document file");
        }
        return filename;
    }

    public Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new DomainException("This document has no file attached");
        }
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new DomainException("Document file not found");
        }
        return target;
    }

    /**
     * Deletes stored files that no document claims and that are older than {@code minAge}.
     *
     * <p>Uploading is a separate step from registering the boleta — the folio is typed the moment
     * the document is emitted, the PDF often arrives later — so abandoning the drawer mid-way
     * leaves a file nothing points at. One at a time is nothing; never cleaning is a directory that
     * only grows.
     *
     * <p>The age threshold is the whole safety of this: a file uploaded seconds ago is on its way
     * to being claimed, and deleting it would break the very flow it belongs to.
     *
     * @param claimed names currently referenced by a document, voided ones included — a voided
     *                boleta keeps its file, since that is the record of what was voided
     * @return how many files were removed
     */
    public int deleteOrphans(Set<String> claimed, Duration minAge) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(minAge);
        int removed = 0;
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (claimed.contains(name)) {
                    continue;
                }
                if (Files.getLastModifiedTime(file).toInstant().isAfter(cutoff)) {
                    continue;
                }
                Files.deleteIfExists(file);
                removed++;
            }
        } catch (IOException e) {
            // A sweep that cannot read the directory is not worth failing a scheduled run over.
            return removed;
        }
        return removed;
    }

    public String contentTypeOf(String storedName) {
        return StoredFileType.of(storedName);
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new DomainException("The document file needs an extension");
        }
        String extension = originalFilename
                .substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DomainException("Unsupported document format: " + extension);
        }
        return extension;
    }
}
