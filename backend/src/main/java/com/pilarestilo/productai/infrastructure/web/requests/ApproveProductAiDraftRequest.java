package com.pilarestilo.productai.infrastructure.web.requests;

import java.util.UUID;

public record ApproveProductAiDraftRequest(
        UUID selectedAssetId,
        OverrideFields override
) {
    public record OverrideFields(
            String name,
            String description,
            String brand
    ) {
    }
}
