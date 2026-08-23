package com.pilarestilo.billing.application.mappers;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.domain.model.SalesDocument;

public final class SalesDocumentMapper {

    private SalesDocumentMapper() {}

    public static SalesDocumentDto toDto(SalesDocument document) {
        return new SalesDocumentDto(
                document.getId(),
                document.getOrderId(),
                document.getType().name(),
                document.getFolio(),
                document.getIssuedAt(),
                document.getNetAmount().amount(),
                document.getTaxAmount().amount(),
                document.getTaxRate(),
                document.getTotalAmount().amount(),
                document.getTotalAmount().currency(),
                document.getReceiverRut(),
                document.getReceiverBusinessName(),
                document.getReceiverBusinessActivity(),
                document.getReceiverName(),
                document.getReceiverEmail(),
                // The url never leaves the backend: /api/media/** is permitAll, so a link would make
                // a document carrying a RUT and amounts readable by anyone holding it.
                document.getFileUrl() != null,
                document.getStatus().name(),
                document.getVoidedAt(),
                document.getVoidReason(),
                document.getReplacesDocumentId(),
                document.getReferenceCode(),
                document.getIssuedBy()
        );
    }
}
