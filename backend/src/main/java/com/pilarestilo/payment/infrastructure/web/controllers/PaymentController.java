package com.pilarestilo.payment.infrastructure.web.controllers;

import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.dto.PaymentGatewayCheckoutDto;
import com.pilarestilo.payment.application.usecases.CreatePaymentGatewayCheckoutUseCase;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.usecases.GetPaymentByOrderUseCase;
import com.pilarestilo.payment.application.usecases.GetPaymentUseCase;
import com.pilarestilo.payment.application.usecases.ListPaymentsUseCase;
import com.pilarestilo.payment.application.usecases.ProcessPaymentGatewayWebhookUseCase;
import com.pilarestilo.payment.application.usecases.RegisterPaymentUseCase;
import com.pilarestilo.payment.application.usecases.ReviewPaymentUseCase;
import com.pilarestilo.payment.application.usecases.SubmitPaymentProofUseCase;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.infrastructure.security.PaymentGatewayWebhookVerifier;
import com.pilarestilo.payment.infrastructure.web.requests.PaymentGatewayWebhookRequest;
import com.pilarestilo.payment.infrastructure.web.requests.RegisterPaymentRequest;
import com.pilarestilo.payment.infrastructure.web.requests.ReviewPaymentRequest;
import com.pilarestilo.payment.infrastructure.web.requests.SubmitProofRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final SubmitPaymentProofUseCase submitPaymentProofUseCase;
    private final ReviewPaymentUseCase reviewPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final GetPaymentByOrderUseCase getPaymentByOrderUseCase;
    private final ListPaymentsUseCase listPaymentsUseCase;
    private final CreatePaymentGatewayCheckoutUseCase createPaymentGatewayCheckoutUseCase;
    private final ProcessPaymentGatewayWebhookUseCase processPaymentGatewayWebhookUseCase;
    private final PaymentGatewayWebhookVerifier paymentGatewayWebhookVerifier;
    private final GetOrderUseCase getOrderUseCase;

    public PaymentController(RegisterPaymentUseCase registerPaymentUseCase,
                              SubmitPaymentProofUseCase submitPaymentProofUseCase,
                              ReviewPaymentUseCase reviewPaymentUseCase,
                              GetPaymentUseCase getPaymentUseCase,
                              GetPaymentByOrderUseCase getPaymentByOrderUseCase,
                              ListPaymentsUseCase listPaymentsUseCase,
                              CreatePaymentGatewayCheckoutUseCase createPaymentGatewayCheckoutUseCase,
                              ProcessPaymentGatewayWebhookUseCase processPaymentGatewayWebhookUseCase,
                              PaymentGatewayWebhookVerifier paymentGatewayWebhookVerifier,
                              GetOrderUseCase getOrderUseCase) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.submitPaymentProofUseCase = submitPaymentProofUseCase;
        this.reviewPaymentUseCase = reviewPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.getPaymentByOrderUseCase = getPaymentByOrderUseCase;
        this.listPaymentsUseCase = listPaymentsUseCase;
        this.createPaymentGatewayCheckoutUseCase = createPaymentGatewayCheckoutUseCase;
        this.processPaymentGatewayWebhookUseCase = processPaymentGatewayWebhookUseCase;
        this.paymentGatewayWebhookVerifier = paymentGatewayWebhookVerifier;
        this.getOrderUseCase = getOrderUseCase;
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
                                   @Valid @RequestBody SubmitProofRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PaymentDto payment = getPaymentUseCase.execute(id);
        if (currentUser.role() == UserRole.CUSTOMER) {
            var order = getOrderUseCase.execute(payment.orderId());
            if (!order.customerId().equals(currentUser.id())) {
                throw new AccessDeniedException("You can only submit proof for your own payments");
            }
        }
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

    @PostMapping("/{id}/gateway/checkout")
    @PreAuthorize("isAuthenticated()")
    public PaymentGatewayCheckoutDto createGatewayCheckout(@PathVariable UUID id,
                                                           @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PaymentDto payment = getPaymentUseCase.execute(id);
        var order = getOrderUseCase.execute(payment.orderId());
        if (currentUser.role() == UserRole.CUSTOMER && !order.customerId().equals(currentUser.id())) {
            throw new AccessDeniedException("You can only initiate checkout for your own payments");
        }
        return createPaymentGatewayCheckoutUseCase.execute(id);
    }

    @PostMapping("/webhooks/gateway")
    public ResponseEntity<Void> handleGatewayWebhook(
            @RequestHeader(value = "X-Gateway-Signature", required = false) String signature,
            @Valid @RequestBody PaymentGatewayWebhookRequest request
    ) {
        if (!paymentGatewayWebhookVerifier.isValid(signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid gateway signature");
        }
        processPaymentGatewayWebhookUseCase.execute(request.paymentId(), request.gatewayStatus());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public PaymentDto getById(@PathVariable UUID id) {
        return getPaymentUseCase.execute(id);
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public PaymentDto getByOrderId(@PathVariable UUID orderId,
                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        var order = getOrderUseCase.execute(orderId);
        if (currentUser.role() == UserRole.CUSTOMER && !order.customerId().equals(currentUser.id())) {
            throw new AccessDeniedException("You can only access your own payments");
        }
        return getPaymentByOrderUseCase.execute(orderId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Page<PaymentDto> list(@RequestParam(required = false) String status, Pageable pageable) {
        PaymentStatus paymentStatus = status != null ? PaymentStatus.valueOf(status.toUpperCase()) : null;
        return listPaymentsUseCase.execute(paymentStatus, pageable);
    }
}
