package com.pilarestilo.payment.infrastructure.storage;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Private storage for transfer receipts, next to the boleta files and for the same reason.
 *
 * <p>A receipt is a screenshot of a bank transfer: it shows the buyer's name, their account, the
 * shop's account and the amount. Until now it was written under {@code app.media.storage-path},
 * whose whole tree {@code MediaResourceConfig} maps to {@code /api/media/**} and
 * {@code SecurityConfig} declares {@code permitAll} — so anyone holding the url could read someone
 * else's bank details, with no session at all.
 *
 * <p>New receipts land here, outside any statically served root, and are read back only through
 * {@code PaymentProofController}. Receipts uploaded before this change still carry their old
 * {@code /api/media/payment-proofs/...} reference in the database, so {@link #resolve(String)} also
 * reads from the legacy location — the public route to it is now denied, but the file itself is
 * still the record of a real payment and the panel has to be able to open it.
 */
@Component
public class PaymentProofStorage {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String LEGACY_PREFIX = "/api/media/";
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final Path root;
    private final Path legacyMediaRoot;

    public PaymentProofStorage(@Value("${app.documents.storage-path:./documents}") String documentsPath,
                               @Value("${app.media.storage-path:./media}") String mediaPath) {
        this.root = Paths.get(documentsPath).toAbsolutePath().normalize().resolve("payment-proofs");
        this.legacyMediaRoot = Paths.get(mediaPath).toAbsolutePath().normalize();
    }

    /** @return an opaque filename stored on the payment row; never a url. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("The proof file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("The proof file exceeds 10 MB");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new DomainException("Invalid proof filename");
        }
        try (InputStream data = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DomainException("Could not store the proof file");
        }
        return filename;
    }

    /**
     * Resolves what a payment row points at, whether that is a name written here or one of the old
     * {@code /api/media/payment-proofs/...} urls.
     */
    public Path resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new DomainException("This payment has no proof attached");
        }
        Path target = reference.startsWith(LEGACY_PREFIX)
                ? legacyMediaRoot.resolve(reference.substring(LEGACY_PREFIX.length())).normalize()
                : root.resolve(reference).normalize();
        Path allowedRoot = reference.startsWith(LEGACY_PREFIX) ? legacyMediaRoot : root;
        if (!target.startsWith(allowedRoot) || !Files.isRegularFile(target)) {
            throw new DomainException("Proof file not found");
        }
        return target;
    }

    /** True when the reference names a file this shop stores, rather than a link the buyer pasted. */
    public boolean isStoredFile(String reference) {
        return reference != null
                && !reference.isBlank()
                && !reference.startsWith("http://")
                && !reference.startsWith("https://");
    }

    public String contentTypeOf(String reference) {
        String lower = reference == null ? "" : reference.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot + 1) : "";
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new DomainException("The proof file needs an extension");
        }
        String extension = originalFilename
                .substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DomainException("Unsupported proof format: " + extension);
        }
        return extension;
    }
}
