package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstagramGraphPublisherAdapterTest {

    private final PublicationDispatchPayload payload = new PublicationDispatchPayload(
            UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
            "Chaqueta a solo $49.990", List.of("#pilarestilo"), List.of("https://cdn.example.com/chaqueta.jpg")
    );

    private MetaPublishingConfigResolver instagramConfig() {
        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));
        return configResolver;
    }

    @Test
    void publishes_via_two_calls_and_returns_the_final_post_id() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ABC123/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178923456", result.remotePostId());
        server.verify();
    }

    @Test
    void publishes_a_two_image_carousel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Mira esto", List.of("#pilarestilo"),
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("is_carousel_item=true")))
                .andRespond(withSuccess("{\"id\":\"child-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("child-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("is_carousel_item=true")))
                .andRespond(withSuccess("{\"id\":\"child-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("child-2?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("media_type=CAROUSEL"),
                        org.hamcrest.Matchers.containsString("children=child-1%2Cchild-2"))))
                .andRespond(withSuccess("{\"id\":\"parent-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("parent-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish?creation_id=parent-1")))
                .andRespond(withSuccess("{\"id\":\"178999\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("178999?fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ZZZ/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178999", result.remotePostId());
        server.verify();
    }

    @Test
    void a_failed_carousel_child_fails_the_whole_publication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "c", List.of(), List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("is_carousel_item=true")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
    }

    @Test
    void polls_the_container_until_it_is_finished_before_publishing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ABC123/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178923456", result.remotePostId());
        server.verify();
    }

    @Test
    void fails_without_publishing_when_the_container_reports_an_error() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"ERROR\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
        server.verify();
    }

    @Test
    void fails_when_the_container_never_becomes_ready() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(10), requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
        assertTrue(result.errorMessage().contains("not ready"), result.errorMessage());
        server.verify();
    }

    @Test
    void returns_a_failed_result_when_meta_rejects_the_request() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
    }

    @Test
    void returns_a_failed_result_when_credentials_are_not_configured() {
        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(RestClient.builder(), configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
    }

    @Test
    void fetches_the_permalink_after_publishing_and_returns_it() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/178923456?fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ABC123/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("https://www.instagram.com/p/ABC123/", result.remotePermalink());
        server.verify();
    }

    @Test
    void still_succeeds_when_the_permalink_fetch_fails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("creation-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("fields=permalink")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals(null, result.remotePermalink());
    }
}
