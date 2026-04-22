package com.pilarestilo.paymentservice.web;

import com.pilarestilo.paymentservice.application.PaymentQueryService;
import com.pilarestilo.paymentservice.auth.AuthenticatedUser;
import com.pilarestilo.paymentservice.auth.UserRole;
import com.pilarestilo.paymentservice.web.dto.PaymentDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentQueryService queryService;

    public PaymentController(PaymentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Page<PaymentDto> list(
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return queryService.list(status, pageable)
                .map(PaymentMapper::toDto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public PaymentDto getById(@PathVariable UUID id) {
        try {
            return PaymentMapper.toDto(queryService.getById(id));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public PaymentDto getByOrderId(@PathVariable UUID orderId,
                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            var order = queryService.getOrderById(orderId);
            if (!currentUser.internalCall()
                    && currentUser.role() == UserRole.CUSTOMER
                    && !order.getCustomerId().equals(currentUser.id())) {
                throw new AccessDeniedException("You can only access your own payments");
            }
            return PaymentMapper.toDto(queryService.getByOrderId(orderId));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }
}
