package com.pilarestilo.publication.domain.model;

public record PostMetrics(
        Long impressions, Long reach, Long likes, Long comments, Long shares, Long saved) {

    public static PostMetrics empty() {
        return new PostMetrics(null, null, null, null, null, null);
    }
}
