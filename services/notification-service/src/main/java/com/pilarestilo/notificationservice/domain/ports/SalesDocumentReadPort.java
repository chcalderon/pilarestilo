package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.SalesDocumentView;

import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentReadPort {
    Optional<SalesDocumentView> findById(UUID documentId);
}
