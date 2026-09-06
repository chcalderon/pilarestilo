package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.springframework.stereotype.Component;

@Component
public class MetaMetricsFetcher implements PublicationMetricsFetcher {

    private final InstagramMetricsFetcher instagram;
    private final FacebookMetricsFetcher facebook;

    public MetaMetricsFetcher(InstagramMetricsFetcher instagram, FacebookMetricsFetcher facebook) {
        this.instagram = instagram;
        this.facebook = facebook;
    }

    @Override
    public Result fetch(PublicationPlatform platform, String externalPostId) {
        return switch (platform) {
            case INSTAGRAM -> instagram.fetch(externalPostId);
            case FACEBOOK -> facebook.fetch(externalPostId);
        };
    }
}
