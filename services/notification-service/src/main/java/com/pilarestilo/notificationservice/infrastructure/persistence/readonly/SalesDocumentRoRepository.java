package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalesDocumentRoRepository extends JpaRepository<SalesDocumentRoEntity, UUID> {
}
