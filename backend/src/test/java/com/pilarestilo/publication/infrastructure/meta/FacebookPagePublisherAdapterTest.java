package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookPagePublisherAdapterTest {

    private final PublicationDispatchPayload payload = new PublicationDispatchPayload(
            UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
            "Chaqueta a solo $49.990", List.of("#pilarestilo"), List.of("https://cdn.example.com/chaqueta.jpg")
    );

    @Test
    void publishes_via_one_call_and_returns_the_post_id() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/1023624300843445/photos")))
                .andRespond(withSuccess("{\"post_id\":\"1023624300843445_555\",\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("1023624300843445_555", result.remotePostId());
        server.verify();
    }

    @Test
    void parses_a_json_body_returned_as_text_javascript() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        // The real Graph API returns JSON with this content type.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/photos")))
                .andRespond(withSuccess("{\"post_id\":\"1023624300843445_777\",\"id\":\"777\"}",
                        MediaType.parseMediaType("text/javascript;charset=UTF-8")));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("1023624300843445_777", result.remotePostId());
        assertEquals("https://www.facebook.com/1023624300843445_777", result.remotePermalink());
    }

    @Test
    void publishes_a_carousel_via_unpublished_photos_and_a_feed_post() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                java.util.UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "Mira esto", List.of("#pilarestilo"),
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/1023624300843445/photos"),
                        org.hamcrest.Matchers.containsString("published=false"))))
                .andRespond(withSuccess("{\"id\":\"photo-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("published=false")))
                .andRespond(withSuccess("{\"id\":\"photo-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/1023624300843445/feed"),
                        org.hamcrest.Matchers.containsString("media_fbid"))))
                .andRespond(withSuccess("{\"id\":\"1023624300843445_9999\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("1023624300843445_9999", result.remotePostId());
        assertEquals("https://www.facebook.com/1023624300843445_9999", result.remotePermalink());
        server.verify();
    }

    @Test
    void a_failed_carousel_photo_upload_fails_the_publication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                java.util.UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "c", List.of(), List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("published=false")))
                .andRespond(withServerError());

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("FACEBOOK_PUBLISH_ERROR", result.errorCode());
    }

    @Test
    void returns_a_failed_result_when_credentials_are_not_configured() {
        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(RestClient.builder(), configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
    }

    @Test
    void builds_the_permalink_from_the_post_id() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/1023624300843445/photos")))
                .andRespond(withSuccess("{\"post_id\":\"1023624300843445_555\",\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals("https://www.facebook.com/1023624300843445_555", result.remotePermalink());
    }

    @Test
    void leaves_the_permalink_null_when_only_a_bare_photo_id_is_returned() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/photos")))
                .andRespond(withSuccess("{\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(null, result.remotePermalink());
    }
}
