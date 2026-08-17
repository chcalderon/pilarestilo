package com.pilarestilo.billing.infrastructure.persistence.repositories;

import com.pilarestilo.billing.infrastructure.persistence.entities.SalesDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentJpaRepository extends JpaRepository<SalesDocumentEntity, UUID> {

    Optional<SalesDocumentEntity> findByOrderIdAndStatusNot(UUID orderId, String status);

    List<SalesDocumentEntity> findByOrderIdOrderByIssuedAtDesc(UUID orderId);

    boolean existsByDocumentTypeAndFolio(String documentType, String folio);
}
