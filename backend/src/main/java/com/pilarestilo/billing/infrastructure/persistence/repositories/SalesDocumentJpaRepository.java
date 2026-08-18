package com.pilarestilo.billing.infrastructure.persistence.repositories;

import com.pilarestilo.billing.infrastructure.persistence.entities.SalesDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentJpaRepository extends JpaRepository<SalesDocumentEntity, UUID> {

    Optional<SalesDocumentEntity> findByOrderIdAndStatusNotAndDocumentTypeNot(
            UUID orderId, String status, String documentType);

    List<SalesDocumentEntity> findByReplacesDocumentIdAndStatusNot(UUID replacesDocumentId, String status);

    List<SalesDocumentEntity> findByOrderIdOrderByIssuedAtDesc(UUID orderId);

    boolean existsByDocumentTypeAndFolio(String documentType, String folio);

    @Query("select d.fileUrl from SalesDocumentEntity d where d.fileUrl is not null")
    List<String> findAllFileUrls();
}
