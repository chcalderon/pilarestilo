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

class InstagramGraphPublisherAdapterTest {

    private final PublicationDispatchPayload payload = new PublicationDispatchPayload(
            UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
            "Chaqueta a solo $49.990", List.of("#pilarestilo"), "https://cdn.example.com/chaqueta.jpg"
    );

    @Test
    void publishes_via_two_calls_and_returns_the_final_post_id() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178923456", result.remotePostId());
        server.verify();
    }

    @Test
    void returns_a_failed_result_when_meta_rejects_the_request() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
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

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(RestClient.builder(), configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
    }
}
