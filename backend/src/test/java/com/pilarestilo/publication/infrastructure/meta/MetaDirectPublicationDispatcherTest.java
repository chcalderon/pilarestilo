package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaDirectPublicationDispatcherTest {

    @Mock InstagramGraphPublisherAdapter instagram;
    @Mock FacebookPagePublisherAdapter facebook;
    @Mock MetaPublishingConfigResolver configResolver;

    MetaDirectPublicationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MetaDirectPublicationDispatcher(instagram, facebook, configResolver);
    }

    @Test
    void routes_instagram_platform_to_the_instagram_adapter_with_an_absolute_url() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", "https://pilarestilo.com"));
        when(instagram.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-1", null, PublicationAttemptStatus.SUCCEEDED, "post-1", null, null, null, true));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), List.of("/api/media/products/x.jpg"));

        PublicationDispatcher.DispatchResult result = dispatcher.dispatch(UUID.randomUUID(), "idem-1", payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(instagram).publish(captor.capture());
        assertEquals("https://pilarestilo.com/api/media/products/x.jpg", captor.getValue().mediaUrls().get(0));
    }

    @Test
    void resolves_every_relative_url_in_the_list_to_absolute() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", "https://pilarestilo.com"));
        when(instagram.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-1", null, PublicationAttemptStatus.SUCCEEDED, "post-1", null, null, null, true));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), List.of("/api/media/products/a.jpg", "https://cdn.example.com/b.jpg"));

        dispatcher.dispatch(UUID.randomUUID(), "idem-1", payload);

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(instagram).publish(captor.capture());
        assertEquals(
                List.of("https://pilarestilo.com/api/media/products/a.jpg", "https://cdn.example.com/b.jpg"),
                captor.getValue().mediaUrls());
    }

    @Test
    void leaves_an_already_absolute_url_untouched() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0", "fb-page", "fb-token",
                "https://graph.facebook.com/v23.0", null));
        when(facebook.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-2", null, PublicationAttemptStatus.SUCCEEDED, "post-2", null, null, null, true));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "Caption", List.of(), List.of("https://cdn.example.com/x.jpg"));

        dispatcher.dispatch(UUID.randomUUID(), "idem-2", payload);

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(facebook).publish(captor.capture());
        assertEquals("https://cdn.example.com/x.jpg", captor.getValue().mediaUrls().get(0));
    }

    @Test
    void throws_when_url_is_relative_and_no_public_base_is_configured() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), List.of("/api/media/products/x.jpg"));

        assertThrows(DomainException.class, () -> dispatcher.dispatch(UUID.randomUUID(), "idem-3", payload));
    }
}
