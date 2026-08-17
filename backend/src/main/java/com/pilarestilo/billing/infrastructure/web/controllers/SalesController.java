package com.pilarestilo.billing.infrastructure.web.controllers;

import com.pilarestilo.billing.application.usecases.ListSalesUseCase;
import com.pilarestilo.billing.domain.model.SaleSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/sales")
public class SalesController {

    private final ListSalesUseCase listSalesUseCase;

    public SalesController(ListSalesUseCase listSalesUseCase) {
        this.listSalesUseCase = listSalesUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_READ)")
    public Page<SaleSummary> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "false") boolean missingDocument,
                                  Pageable pageable) {
        return listSalesUseCase.execute(q, status, missingDocument, pageable);
    }

    /** Feeds the counter next to Ventas, so an undeclared sale is visible without opening the list. */
    @GetMapping("/pending-documents/count")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_READ)")
    public Map<String, Long> pendingDocuments() {
        return Map.of("count", listSalesUseCase.countMissingDocuments());
    }
}
