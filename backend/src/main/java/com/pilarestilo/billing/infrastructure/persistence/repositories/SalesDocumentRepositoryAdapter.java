package com.pilarestilo.billing.infrastructure.persistence.repositories;

import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.billing.infrastructure.persistence.entities.SalesDocumentEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class SalesDocumentRepositoryAdapter implements SalesDocumentRepository {

    private final SalesDocumentJpaRepository jpaRepository;

    public SalesDocumentRepositoryAdapter(SalesDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SalesDocument save(SalesDocument document) {
        return toDomain(jpaRepository.save(toEntity(document)));
    }

    @Override
    public Optional<SalesDocument> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SalesDocument> findLiveByOrderId(UUID orderId) {
        return jpaRepository
                .findByOrderIdAndStatusNotAndDocumentTypeNot(
                        orderId,
                        SalesDocumentStatus.VOIDED.name(),
                        // A credit note is live alongside the boleta it counterweighs, by design.
                        // Including it here would make this read find two rows and throw.
                        SalesDocumentType.NOTA_CREDITO.name())
                .map(this::toDomain);
    }

    @Override
    public List<SalesDocument> findAllByOrderId(UUID orderId) {
        return jpaRepository.findByOrderIdOrderByIssuedAtDesc(orderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SalesDocument> findLiveCreditNotesFor(UUID documentId) {
        return jpaRepository
                .findByReplacesDocumentIdAndStatusNot(documentId, SalesDocumentStatus.VOIDED.name())
                .stream()
                .map(this::toDomain)
                .filter(document -> document.getType() == SalesDocumentType.NOTA_CREDITO)
                .toList();
    }

    @Override
    public boolean existsByTypeAndFolio(SalesDocumentType type, String folio) {
        return jpaRepository.existsByDocumentTypeAndFolio(type.name(), folio);
    }

    @Override
    public Set<String> findAllStoredFileNames() {
        return Set.copyOf(jpaRepository.findAllFileUrls());
    }

    private SalesDocumentEntity toEntity(SalesDocument document) {
        SalesDocumentEntity entity = new SalesDocumentEntity();
        entity.setId(document.getId());
        entity.setOrderId(document.getOrderId());
        entity.setDocumentType(document.getType().name());
        entity.setFolio(document.getFolio());
        entity.setIssuedAt(document.getIssuedAt());
        entity.setNetAmount(document.getNetAmount().amount());
        entity.setTaxAmount(document.getTaxAmount().amount());
        entity.setTaxRate(document.getTaxRate());
        entity.setTotalAmount(document.getTotalAmount().amount());
        entity.setCurrency(document.getTotalAmount().currency());
        entity.setReceiverRut(document.getReceiverRut());
        entity.setReceiverName(document.getReceiverName());
        entity.setReceiverEmail(document.getReceiverEmail());
        entity.setFileUrl(document.getFileUrl());
        entity.setStatus(document.getStatus().name());
        entity.setVoidedAt(document.getVoidedAt());
        entity.setVoidReason(document.getVoidReason());
        entity.setVoidedBy(document.getVoidedBy());
        entity.setReplacesDocumentId(document.getReplacesDocumentId());
        entity.setReferenceCode(document.getReferenceCode());
        entity.setIssuedBy(document.getIssuedBy());
        entity.setCreatedAt(document.getCreatedAt());
        return entity;
    }

    private SalesDocument toDomain(SalesDocumentEntity entity) {
        String currency = entity.getCurrency();
        return SalesDocument.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                SalesDocumentType.valueOf(entity.getDocumentType()),
                entity.getFolio(),
                entity.getIssuedAt(),
                Money.of(entity.getNetAmount(), currency),
                Money.of(entity.getTaxAmount(), currency),
                entity.getTaxRate(),
                Money.of(entity.getTotalAmount(), currency),
                entity.getReceiverRut(),
                entity.getReceiverName(),
                entity.getReceiverEmail(),
                entity.getFileUrl(),
                SalesDocumentStatus.valueOf(entity.getStatus()),
                entity.getVoidedAt(),
                entity.getVoidReason(),
                entity.getVoidedBy(),
                entity.getReplacesDocumentId(),
                entity.getReferenceCode(),
                entity.getIssuedBy(),
                entity.getCreatedAt()
        );
    }
}
