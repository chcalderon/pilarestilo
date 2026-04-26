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
            // deletion failure is non-critical
        }
    }
}
