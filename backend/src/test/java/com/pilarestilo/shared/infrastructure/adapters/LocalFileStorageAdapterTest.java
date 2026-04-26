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
