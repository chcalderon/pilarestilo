package com.pilarestilo.billing.infrastructure;

import com.pilarestilo.billing.infrastructure.storage.SalesDocumentFileStorage;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesDocumentFileStorageTest {

    @TempDir
    Path root;

    private SalesDocumentFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SalesDocumentFileStorage(root.toString());
    }

    @Test
    void stores_a_pdf_and_reads_it_back() {
        String stored = storage.store(pdf("boleta.pdf"));

        assertTrue(stored.endsWith(".pdf"));
        // Never a url: the file lives outside the media root precisely so it cannot be fetched by one.
        assertFalse(stored.startsWith("/"));
        assertEquals("application/pdf", storage.contentTypeOf(stored));
        assertTrue(Files.exists(storage.resolve(stored)));
    }

    @Test
    void refuses_a_format_that_is_not_a_document() {
        DomainException ex = assertThrows(DomainException.class,
                () -> storage.store(new MockMultipartFile("file", "malware.exe",
                        "application/octet-stream", new byte[]{1, 2, 3})));

        assertTrue(ex.getMessage().contains("exe"));
    }

    @Test
    void refuses_a_file_with_no_extension_and_an_empty_one() {
        assertThrows(DomainException.class, () -> storage.store(
                new MockMultipartFile("file", "sinextension", "application/pdf", new byte[]{1})));
        assertThrows(DomainException.class, () -> storage.store(
                new MockMultipartFile("file", "vacio.pdf", "application/pdf", new byte[0])));
    }

    /** Path traversal cannot reach outside the root, and a name nothing stored is not a file. */
    @Test
    void refuses_to_resolve_anything_outside_the_root() {
        assertThrows(DomainException.class, () -> storage.resolve("../../etc/passwd"));
        assertThrows(DomainException.class, () -> storage.resolve("no-existe.pdf"));
        assertThrows(DomainException.class, () -> storage.resolve("  "));
    }

    @Test
    void sweeps_an_old_file_that_no_document_claims() throws IOException {
        String orphan = storage.store(pdf("huerfana.pdf"));
        age(orphan, Duration.ofDays(2));

        int removed = storage.deleteOrphans(Set.of(), Duration.ofHours(24));

        assertEquals(1, removed);
        assertFalse(Files.exists(root.resolve(orphan)));
    }

    /** A claimed file stays however old it is: a voided boleta keeps the record of what was voided. */
    @Test
    void never_sweeps_a_file_a_document_points_at() throws IOException {
        String claimed = storage.store(pdf("emitida.pdf"));
        age(claimed, Duration.ofDays(400));

        int removed = storage.deleteOrphans(Set.of(claimed), Duration.ofHours(24));

        assertEquals(0, removed);
        assertTrue(Files.exists(root.resolve(claimed)));
    }

    /**
     * The safety of the whole sweep. A file uploaded seconds ago is on its way to being claimed by
     * the issue call that follows it; deleting it would break the flow it belongs to.
     */
    @Test
    void never_sweeps_a_file_that_was_just_uploaded() {
        String fresh = storage.store(pdf("recien-subida.pdf"));

        int removed = storage.deleteOrphans(Set.of(), Duration.ofHours(24));

        assertEquals(0, removed);
        assertTrue(Files.exists(root.resolve(fresh)));
    }

    @Test
    void a_missing_directory_sweeps_nothing_rather_than_failing() {
        SalesDocumentFileStorage empty =
                new SalesDocumentFileStorage(root.resolve("todavia-no-existe").toString());

        assertEquals(0, empty.deleteOrphans(Set.of(), Duration.ofHours(24)));
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4 fake".getBytes());
    }

    private void age(String storedName, Duration by) throws IOException {
        Files.setLastModifiedTime(root.resolve(storedName),
                FileTime.from(Instant.now().minus(by)));
    }
}
