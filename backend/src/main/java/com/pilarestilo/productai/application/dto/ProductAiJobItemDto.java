package com.pilarestilo.productai.application.dto;

import java.util.UUID;

public record ProductAiJobItemDto(
        UUID assetId,
        String title,
        String description,
        String imagePrompt,
        String processedMasterUrl,
        String processedWebUrl,
        String processedThumbUrl
) {
}
