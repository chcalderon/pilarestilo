package com.pilarestilo.returns.infrastructure.persistence.repositories;

import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.enums.ReturnStatus;
import com.pilarestilo.returns.domain.model.RefundAccount;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.returns.infrastructure.persistence.entities.ReturnRequestEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReturnRequestRepositoryAdapter implements ReturnRequestRepository {

    private final ReturnRequestJpaRepository jpaRepository;

    public ReturnRequestRepositoryAdapter(ReturnRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReturnRequest save(ReturnRequest request) {
        return toDomain(jpaRepository.save(toEntity(request)));
    }

    @Override
    public Optional<ReturnRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ReturnRequest> findOpenByOrderId(UUID orderId) {
        return jpaRepository.findOpenByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public List<ReturnRequest> findAllByOrderId(UUID orderId) {
        return jpaRepository.findByOrderIdOrderByRequestedAtDesc(orderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Page<ReturnRequest> findOpenByDeadline(Pageable pageable) {
        return jpaRepository.findOpenByDeadline(pageable).map(this::toDomain);
    }

    @Override
    public Page<ReturnRequest> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public List<ReturnRequest> findByRequestedBy(UUID userId) {
        return jpaRepository.findByRequestedByOrderByRequestedAtDesc(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    private ReturnRequestEntity toEntity(ReturnRequest request) {
        ReturnRequestEntity entity = new ReturnRequestEntity();
        entity.setId(request.getId());
        entity.setOrderId(request.getOrderId());
        entity.setKind(request.getKind().name());
        entity.setStatus(request.getStatus().name());
        entity.setReason(request.getReason());
        entity.setRequestedBy(request.getRequestedBy());
        entity.setRequestedAt(request.getRequestedAt());
        entity.setDeadlineAt(request.getDeadlineAt());
        entity.setResolvedAt(request.getResolvedAt());
        entity.setResolutionNote(request.getResolutionNote());
        entity.setItemDisposition(request.getItemDisposition() == null
                ? null : request.getItemDisposition().name());
        entity.setDispositionAt(request.getDispositionAt());
        entity.setDispositionNote(request.getDispositionNote());
        entity.setRefundAmount(request.getRefundAmount() == null
                ? null : request.getRefundAmount().amount());
        entity.setRefundCurrency(request.getRefundAmount() == null
                ? null : request.getRefundAmount().currency());
        entity.setRefundMethod(request.getRefundMethod() == null
                ? null : request.getRefundMethod().name());
        entity.setRefundReference(request.getRefundReference());
        entity.setRefundFileUrl(request.getRefundFileUrl());
        entity.setRefundedAt(request.getRefundedAt());

        RefundAccount account = request.getRefundAccount();
        entity.setRefundAccountHolder(account == null ? null : account.holder());
        entity.setRefundAccountRut(account == null ? null : account.rut());
        entity.setRefundBankName(account == null ? null : account.bankName());
        entity.setRefundAccountType(account == null ? null : account.accountType());
        entity.setRefundAccountEncrypted(account == null ? null : account.numberEncrypted());
        entity.setRefundAccountLast4(account == null ? null : account.last4());

        entity.setCreditNoteId(request.getCreditNoteId());
        entity.setCreatedAt(request.getCreatedAt());
        return entity;
    }

    private ReturnRequest toDomain(ReturnRequestEntity entity) {
        Money refund = entity.getRefundAmount() == null
                ? null
                : Money.of(entity.getRefundAmount(), entity.getRefundCurrency());
        RefundAccount account = entity.getRefundAccountHolder() == null
                ? null
                : new RefundAccount(
                        entity.getRefundAccountHolder(),
                        entity.getRefundAccountRut(),
                        entity.getRefundBankName(),
                        entity.getRefundAccountType(),
                        entity.getRefundAccountEncrypted(),
                        entity.getRefundAccountLast4());

        return ReturnRequest.reconstruct(
                entity.getId(),
                entity.getOrderId(),
                ReturnKind.valueOf(entity.getKind()),
                ReturnStatus.valueOf(entity.getStatus()),
                entity.getReason(),
                entity.getRequestedBy(),
                entity.getRequestedAt(),
                entity.getDeadlineAt(),
                entity.getResolvedAt(),
                entity.getResolutionNote(),
                entity.getItemDisposition() == null
                        ? null : ItemDisposition.valueOf(entity.getItemDisposition()),
                entity.getDispositionAt(),
                entity.getDispositionNote(),
                refund,
                entity.getRefundMethod() == null ? null : RefundMethod.valueOf(entity.getRefundMethod()),
                entity.getRefundReference(),
                entity.getRefundFileUrl(),
                entity.getRefundedAt(),
                account,
                entity.getCreditNoteId(),
                entity.getCreatedAt()
        );
    }
}
