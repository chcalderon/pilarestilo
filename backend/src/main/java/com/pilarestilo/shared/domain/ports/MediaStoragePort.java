package com.pilarestilo.shared.domain.ports;

import java.io.InputStream;

public interface MediaStoragePort {
    String store(InputStream data, String folder, String filename, String contentType);
    void delete(String folder, String filename);
}
