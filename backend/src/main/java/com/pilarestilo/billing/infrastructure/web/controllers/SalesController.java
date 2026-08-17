package com.pilarestilo.billing.infrastructure.web.controllers;

import com.pilarestilo.billing.application.usecases.CancelSaleUseCase;
import com.pilarestilo.billing.application.usecases.ListSalesUseCase;
import com.pilarestilo.billing.domain.model.SaleSummary;
import com.pilarestilo.billing.infrastructure.web.requests.CancelSaleRequest;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sales")
public class SalesController {

    private final ListSalesUseCase listSalesUseCase;
    private final CancelSaleUseCase cancelSaleUseCase;

    public SalesController(ListSalesUseCase listSalesUseCase,
                           CancelSaleUseCase cancelSaleUseCase) {
        this.listSalesUseCase = listSalesUseCase;
        this.cancelSaleUseCase = cancelSaleUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_READ)")
    public Page<SaleSummary> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "false") boolean missingDocument,
                                  Pageable pageable) {
        return listSalesUseCase.execute(q, status, missingDocument, pageable);
    }

    /**
     * Voids the sale's document and cancels the order in one step, which is also what returns the
     * units to the shelf. Guarded by {@code orders.update}: undoing a sale that took money is a
     * heavier act than correcting a folio, and only ADMIN holds it.
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_UPDATE)")
    public OrderDto cancel(@PathVariable UUID orderId,
                           @Valid @RequestBody CancelSaleRequest request,
                           @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return cancelSaleUseCase.execute(
                orderId, request.reason(), currentUser == null ? null : currentUser.id());
    }

    /** Feeds the counter next to Ventas, so an undeclared sale is visible without opening the list. */
    @GetMapping("/pending-documents/count")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).DOCUMENTS_READ)")
    public Map<String, Long> pendingDocuments() {
        return Map.of("count", listSalesUseCase.countMissingDocuments());
    }
}
