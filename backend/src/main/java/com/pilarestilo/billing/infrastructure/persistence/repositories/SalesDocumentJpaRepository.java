package com.pilarestilo.billing.infrastructure.persistence.repositories;

import com.pilarestilo.billing.infrastructure.persistence.entities.SalesDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentJpaRepository extends JpaRepository<SalesDocumentEntity, UUID> {

    Optional<SalesDocumentEntity> findByOrderIdAndStatusNotAndDocumentTypeNot(
            UUID orderId, String status, String documentType);

    List<SalesDocumentEntity> findByReplacesDocumentIdAndStatusNot(UUID replacesDocumentId, String status);

    List<SalesDocumentEntity> findByOrderIdOrderByIssuedAtDesc(UUID orderId);

    boolean existsByDocumentTypeAndFolio(String documentType, String folio);

    /** Same exclusions as the single-order read: voided documents and credit notes do not count. */
    @Query("""
            select distinct d.orderId from SalesDocumentEntity d
            where d.orderId in :orderIds
              and d.status <> :voided
              and d.documentType <> :creditNote
            """)
    List<UUID> findOrderIdsWithLiveDocument(Collection<UUID> orderIds, String voided, String creditNote);

    @Query("select d.fileUrl from SalesDocumentEntity d where d.fileUrl is not null")
    List<String> findAllFileUrls();

    /** Native: a folio can be typed as anything the operator wants (it's a plain VARCHAR), so the
     * `~ '^[0-9]+$'` guard keeps a stray non-numeric one from breaking the cast rather than just
     * being excluded from the max. */
    @Query(value = """
            SELECT MAX(CAST(folio AS BIGINT))
            FROM sales_documents
            WHERE document_type = :documentType
              AND folio ~ '^[0-9]+$'
            """, nativeQuery = true)
    Long findMaxNumericFolio(String documentType);
}
