package com.pilarestilo.productai.application.dto;

import java.util.UUID;

public record ProductAiUploadedAssetDto(
        UUID assetId,
        String originalUrl,
        String filename
) {
}
