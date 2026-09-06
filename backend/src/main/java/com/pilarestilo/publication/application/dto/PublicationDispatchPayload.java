package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.UUID;

public record PublicationDispatchPayload(
        UUID productId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String caption,
        List<String> hashtags,
        List<String> mediaUrls
) {
    public String fullCaptionText() {
        String base = caption == null ? "" : caption.trim();
        if (hashtags == null || hashtags.isEmpty()) {
            return base;
        }
        String tags = String.join(" ", hashtags);
        return base.isEmpty() ? tags : base + "\n\n" + tags;
    }
}
