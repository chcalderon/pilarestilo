package com.pilarestilo.billing.domain.ports;

import com.pilarestilo.billing.domain.model.SaleSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SalesQueryRepository {

    /**
     * @param query        free text matched against the public reference, buyer name and email
     * @param orderStatus  exact order status, or null for any
     * @param missingOnly  only paid sales with no live document — the "boletas pendientes" queue
     */
    Page<SaleSummary> search(String query, String orderStatus, boolean missingOnly, Pageable pageable);

    /** How many paid sales are still undeclared. Drives the badge next to Ventas. */
    long countMissingDocuments();
}
