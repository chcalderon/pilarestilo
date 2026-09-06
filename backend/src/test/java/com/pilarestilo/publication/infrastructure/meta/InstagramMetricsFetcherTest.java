package com.pilarestilo.publication.infrastructure.meta;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstagramMetricsFetcherTest {

    private MetaPublishingConfigResolver config() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null));
        return r;
    }

    @Test
    void reads_like_and_comment_counts_and_insight_metrics() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1?fields=like_count")))
                .andRespond(withSuccess("{\"like_count\":45,\"comments_count\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1/insights")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"name\":\"reach\",\"values\":[{\"value\":900}]},"
                        + "{\"name\":\"saved\",\"values\":[{\"value\":12}]}]}",
                        MediaType.APPLICATION_JSON));

        var result = new InstagramMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("m-1");

        assertTrue(result.metrics().isPresent());
        assertEquals(45L, result.metrics().get().likes());
        assertEquals(3L, result.metrics().get().comments());
        assertEquals(900L, result.metrics().get().reach());
        assertEquals(12L, result.metrics().get().saved());
        assertNull(result.metrics().get().impressions());
        server.verify();
    }

    @Test
    void returns_failed_on_a_graph_error() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1?fields=like_count")))
                .andRespond(withServerError());

        var result = new InstagramMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("m-1");

        assertFalse(result.metrics().isPresent());
    }

    @Test
    void returns_failed_when_credentials_are_missing() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", null));

        var result = new InstagramMetricsFetcher(RestClient.builder(), r, new tools.jackson.databind.ObjectMapper()).fetch("m-1");
        assertFalse(result.metrics().isPresent());
    }
}
