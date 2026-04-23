package com.pilarestilo.systemsettings.domain.enums;

public enum MediaStorageProvider {
    LOCAL,
    S3_COMPATIBLE;

    public static MediaStorageProvider fromRaw(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LOCAL;
        }
        return MediaStorageProvider.valueOf(rawValue.trim().toUpperCase());
    }
}
