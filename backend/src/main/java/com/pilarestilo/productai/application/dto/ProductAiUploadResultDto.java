package com.pilarestilo.productai.application.dto;

import java.util.List;
import java.util.UUID;

public record ProductAiUploadResultDto(
        UUID draftId,
        List<ProductAiUploadedAssetDto> uploaded
) {
}
