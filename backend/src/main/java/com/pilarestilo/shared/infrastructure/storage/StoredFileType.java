package com.pilarestilo.shared.infrastructure.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a stored receipt or tax document is, and how it should be handed back.
 *
 * <p>Both stores keep the same kinds of file for the same reason: somebody photographs a boleta in
 * the SII app, or a transfer receipt in their bank app. They had two copies of this rule and the
 * copies had drifted — the document store accepted png, jpg and webp and then declared every one of
 * them {@code application/octet-stream}, so a boleta the shop had filed correctly came back as a
 * download instead of a picture, and the panel looked like it had lost the file.
 */
public final class StoredFileType {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private StoredFileType() {
    }

    /** The extensions worth accepting, which is exactly the set that can be declared honestly. */
    public static Set<String> allowedExtensions() {
        return CONTENT_TYPES.keySet();
    }

    public static String of(String storedName) {
        String lower = storedName == null ? "" : storedName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot + 1) : "";
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
