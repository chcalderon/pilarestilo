package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;

interface SocialPlatformPublisher {
    PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload);
}
