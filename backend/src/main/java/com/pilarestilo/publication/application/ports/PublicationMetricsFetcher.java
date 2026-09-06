package com.pilarestilo.publication.application.ports;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;

import java.util.Optional;

public interface PublicationMetricsFetcher {

    /** Empty result when the fetch failed for any reason (bad scope, deleted post, network). */
    Result fetch(PublicationPlatform platform, String externalPostId);

    record Result(Optional<PostMetrics> metrics, String error) {
        public static Result ok(PostMetrics m) {
            return new Result(Optional.of(m), null);
        }

        public static Result failed(String error) {
            return new Result(Optional.empty(), error);
        }
    }
}
