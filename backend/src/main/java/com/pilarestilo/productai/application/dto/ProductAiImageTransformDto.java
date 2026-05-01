package com.pilarestilo.productai.application.dto;

public record ProductAiImageTransformDto(
        String processedMasterUrl,
        String processedWebUrl,
        String processedThumbUrl,
        String provider,
        String promptUsed,
        String engine
) {
}
