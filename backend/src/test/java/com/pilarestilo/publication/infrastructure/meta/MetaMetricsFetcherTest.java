package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaMetricsFetcherTest {

    @Mock InstagramMetricsFetcher instagram;
    @Mock FacebookMetricsFetcher facebook;

    @Test
    void routes_instagram_to_the_instagram_fetcher() {
        when(instagram.fetch("m-1")).thenReturn(
                PublicationMetricsFetcher.Result.ok(new PostMetrics(1L, 1L, 1L, 1L, 1L, 1L)));
        var out = new MetaMetricsFetcher(instagram, facebook).fetch(PublicationPlatform.INSTAGRAM, "m-1");
        assertEquals(1L, out.metrics().get().likes());
        verify(instagram).fetch("m-1");
        verifyNoInteractions(facebook);
    }

    @Test
    void routes_facebook_to_the_facebook_fetcher() {
        when(facebook.fetch("p_1")).thenReturn(PublicationMetricsFetcher.Result.failed("nope"));
        var out = new MetaMetricsFetcher(instagram, facebook).fetch(PublicationPlatform.FACEBOOK, "p_1");
        assertEquals("nope", out.error());
        verify(facebook).fetch("p_1");
        verifyNoInteractions(instagram);
    }
}
