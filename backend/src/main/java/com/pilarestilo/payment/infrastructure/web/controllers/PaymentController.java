package com.pilarestilo.payment.infrastructure.web.controllers;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.usecases.*;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.infrastructure.web.requests.RegisterPaymentRequest;
import com.pilarestilo.payment.infrastructure.web.requests.ReviewPaymentRequest;
import com.pilarestilo.payment.infrastructure.web.requests.SubmitProofRequest;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final SubmitPaymentProofUseCase submitPaymentProofUseCase;
    private final ReviewPaymentUseCase reviewPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final ListPaymentsUseCase listPaymentsUseCase;

    public PaymentController(RegisterPaymentUseCase registerPaymentUseCase,
                              SubmitPaymentProofUseCase submitPaymentProofUseCase,
                              ReviewPaymentUseCase reviewPaymentUseCase,
                              GetPaymentUseCase getPaymentUseCase,
                              ListPaymentsUseCase listPaymentsUseCase) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.submitPaymentProofUseCase = submitPaymentProofUseCase;
        this.reviewPaymentUseCase = reviewPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.listPaymentsUseCase = listPaymentsUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<PaymentDto> register(@Valid @RequestBody RegisterPaymentRequest request) {
        PaymentDto dto = registerPaymentUseCase.execute(
                request.orderId(),
                PaymentMethod.valueOf(request.paymentMethod())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}/proof")
    @PreAuthorize("isAuthenticated()")
    public PaymentDto submitProof(@PathVariable UUID id,
                                   @Valid @RequestBody SubmitProofRequest request) {
        return submitPaymentProofUseCase.execute(id, request.proofReference());
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public PaymentDto review(@PathVariable UUID id,
                              @Valid @RequestBody ReviewPaymentRequest request) {
        return switch (request.action().toUpperCase()) {
            case "APPROVE" -> reviewPaymentUseCase.approve(id, request.reviewerId());
            case "REJECT" -> reviewPaymentUseCase.reject(id, request.reviewerId());
            default -> throw new DomainException("Unknown review action: " + request.action() + ". Use APPROVE or REJECT.");
        };
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public PaymentDto getById(@PathVariable UUID id) {
        return getPaymentUseCase.execute(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Page<PaymentDto> list(@RequestParam(required = false) String status, Pageable pageable) {
        PaymentStatus paymentStatus = status != null ? PaymentStatus.valueOf(status.toUpperCase()) : null;
        return listPaymentsUseCase.execute(paymentStatus, pageable);
    }
}
