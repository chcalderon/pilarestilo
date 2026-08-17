package com.pilarestilo.returns.application.mappers;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.domain.model.RefundAccount;
import com.pilarestilo.returns.domain.model.ReturnRequest;

import java.time.Instant;

public final class ReturnRequestMapper {

    private ReturnRequestMapper() {}

    public static ReturnRequestDto toDto(ReturnRequest request) {
        RefundAccount account = request.getRefundAccount();
        return new ReturnRequestDto(
                request.getId(),
                request.getOrderId(),
                request.getKind().name(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedBy(),
                request.getRequestedAt(),
                request.getDeadlineAt(),
                request.daysUntilDeadline(Instant.now()),
                request.getResolvedAt(),
                request.getResolutionNote(),
                request.getItemDisposition() == null ? null : request.getItemDisposition().name(),
                request.getDispositionAt(),
                request.getDispositionNote(),
                request.getRefundAmount() == null ? null : request.getRefundAmount().amount(),
                request.getRefundAmount() == null ? null : request.getRefundAmount().currency(),
                request.getRefundMethod() == null ? null : request.getRefundMethod().name(),
                request.getRefundReference(),
                request.getRefundFileUrl() != null,
                request.getRefundedAt(),
                account == null ? null : account.holder(),
                account == null ? null : account.bankName(),
                account == null ? null : account.accountType(),
                account == null ? null : account.last4(),
                account != null && account.isConfigured(),
                request.getCreditNoteId()
        );
    }
}
