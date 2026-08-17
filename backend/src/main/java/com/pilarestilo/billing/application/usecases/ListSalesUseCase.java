package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.model.SaleSummary;
import com.pilarestilo.billing.domain.ports.SalesQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSalesUseCase {

    private final SalesQueryRepository salesQueryRepository;

    public ListSalesUseCase(SalesQueryRepository salesQueryRepository) {
        this.salesQueryRepository = salesQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<SaleSummary> execute(String query, String orderStatus, boolean missingOnly, Pageable pageable) {
        return salesQueryRepository.search(query, orderStatus, missingOnly, pageable);
    }

    @Transactional(readOnly = true)
    public long countMissingDocuments() {
        return salesQueryRepository.countMissingDocuments();
    }
}
