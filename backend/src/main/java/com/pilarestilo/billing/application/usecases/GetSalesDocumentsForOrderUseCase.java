package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetSalesDocumentsForOrderUseCase {

    private final SalesDocumentRepository salesDocumentRepository;

    public GetSalesDocumentsForOrderUseCase(SalesDocumentRepository salesDocumentRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
    }

    /** Newest first, voided ones included: a correction is only readable next to what it corrected. */
    @Transactional(readOnly = true)
    public List<SalesDocumentDto> execute(UUID orderId) {
        return salesDocumentRepository.findAllByOrderId(orderId).stream()
                .map(SalesDocumentMapper::toDto)
                .toList();
    }
}
