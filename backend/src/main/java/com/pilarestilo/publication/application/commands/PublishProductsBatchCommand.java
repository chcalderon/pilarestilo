package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PublishProductsBatchCommand(
        List<UUID> productIds,
        Set<PublicationPlatform> platforms,
        String captionTemplate,
        List<String> hashtags,
        String campaignLabel
) {}
