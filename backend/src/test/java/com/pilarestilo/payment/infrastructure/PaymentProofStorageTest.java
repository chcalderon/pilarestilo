package com.pilarestilo.payment.infrastructure;

import com.pilarestilo.payment.infrastructure.storage.PaymentProofStorage;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentProofStorageTest {

    @TempDir
    Path documentsRoot;

    @TempDir
    Path mediaRoot;

    private PaymentProofStorage storage;

    @BeforeEach
    void setUp() {
        storage = new PaymentProofStorage(documentsRoot.toString(), mediaRoot.toString());
    }

    @Test
    void stores_a_receipt_outside_the_media_root_and_reads_it_back() {
        String stored = storage.store(image("comprobante.jpg"));

        assertFalse(stored.contains("/"), "a stored name is not a path and never a url");
        Path resolved = storage.resolve(stored);
        assertTrue(resolved.startsWith(documentsRoot), "receipts must not land where /api/media/** serves");
        assertFalse(resolved.startsWith(mediaRoot));
    }

    @Test
    void reads_back_a_receipt_uploaded_before_the_move() throws IOException {
        Path legacyFolder = mediaRoot.resolve("payment-proofs");
        Files.createDirectories(legacyFolder);
        Files.writeString(legacyFolder.resolve("old.jpg"), "bytes");

        Path resolved = storage.resolve("/api/media/payment-proofs/old.jpg");

        assertEquals(legacyFolder.resolve("old.jpg"), resolved);
    }

    @Test
    void refuses_to_climb_out_of_either_root() {
        assertThrows(DomainException.class, () -> storage.resolve("../../etc/passwd"));
        assertThrows(DomainException.class, () -> storage.resolve("/api/media/../../etc/passwd"));
    }

    @Test
    void refuses_an_empty_reference() {
        assertThrows(DomainException.class, () -> storage.resolve(null));
        assertThrows(DomainException.class, () -> storage.resolve("  "));
    }

    @Test
    void refuses_a_format_that_is_not_a_receipt() {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "comprobante.exe", "application/octet-stream", "x".getBytes());
        MockMultipartFile noExtension = new MockMultipartFile(
                "file", "sinextension", "image/jpeg", "x".getBytes());

        assertThrows(DomainException.class, () -> storage.store(exe));
        assertThrows(DomainException.class, () -> storage.store(noExtension));
    }

    @Test
    void refuses_an_empty_file() {
        MockMultipartFile empty = new MockMultipartFile("file", "comprobante.jpg", "image/jpeg", new byte[0]);

        assertThrows(DomainException.class, () -> storage.store(empty));
    }

    /** A buyer may still paste a link to her bank instead of a file; that is not ours to serve. */
    @Test
    void tells_a_stored_file_apart_from_a_pasted_link() {
        assertTrue(storage.isStoredFile("1723-abc.jpg"));
        assertFalse(storage.isStoredFile("https://banco.cl/comprobante/123"));
        assertFalse(storage.isStoredFile(null));
    }

    @Test
    void names_the_content_type_from_the_extension() {
        assertEquals("image/jpeg", storage.contentTypeOf("a.jpg"));
        assertEquals("application/pdf", storage.contentTypeOf("a.PDF"));
        assertEquals("application/octet-stream", storage.contentTypeOf("a"));
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", "bytes".getBytes());
    }
}
