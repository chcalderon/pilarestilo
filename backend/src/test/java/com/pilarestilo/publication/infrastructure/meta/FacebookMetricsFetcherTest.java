package com.pilarestilo.publication.infrastructure.meta;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookMetricsFetcherTest {

    private MetaPublishingConfigResolver config() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "page-1", "token-fb", "https://graph.facebook.com/v23.0", null));
        return r;
    }

    @Test
    void reads_like_comment_share_counts_and_insights() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withSuccess(
                        "{\"likes\":{\"summary\":{\"total_count\":30}},"
                        + "\"comments\":{\"summary\":{\"total_count\":4}},"
                        + "\"shares\":{\"count\":2}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1/insights")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"name\":\"post_impressions\",\"values\":[{\"value\":1200}]},"
                        + "{\"name\":\"post_impressions_unique\",\"values\":[{\"value\":800}]}]}",
                        MediaType.APPLICATION_JSON));

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");

        assertTrue(result.metrics().isPresent());
        assertEquals(30L, result.metrics().get().likes());
        assertEquals(4L, result.metrics().get().comments());
        assertEquals(2L, result.metrics().get().shares());
        assertEquals(1200L, result.metrics().get().impressions());
        assertEquals(800L, result.metrics().get().reach());
        server.verify();
    }

    @Test
    void keeps_the_summary_counts_when_the_insights_call_is_forbidden() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withSuccess("{\"likes\":{\"summary\":{\"total_count\":30}},"
                        + "\"comments\":{\"summary\":{\"total_count\":4}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1/insights")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");

        assertTrue(result.metrics().isPresent());
        assertEquals(30L, result.metrics().get().likes());
        assertNull(result.metrics().get().impressions());
    }

    @Test
    void returns_failed_when_the_summary_call_fails() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withServerError());

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");
        assertFalse(result.metrics().isPresent());
    }
}
