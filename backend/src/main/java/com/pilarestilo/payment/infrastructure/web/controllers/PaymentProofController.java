package com.pilarestilo.payment.infrastructure.web.controllers;

import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.usecases.GetPaymentUseCase;
import com.pilarestilo.payment.infrastructure.storage.PaymentProofStorage;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * The only door to a transfer receipt.
 *
 * <p>Deliberately not under {@code /api/payments}: Caddy routes every GET on that prefix to the
 * extracted payment-service, which knows nothing about this file. And deliberately not under
 * {@code /api/media}, which is public — that is the exposure this class closes.
 */
@RestController
@RequestMapping("/api/payment-proofs")
public class PaymentProofController {

    private final PaymentProofStorage storage;
    private final GetPaymentUseCase getPaymentUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public PaymentProofController(PaymentProofStorage storage,
                                  GetPaymentUseCase getPaymentUseCase,
                                  GetOrderUseCase getOrderUseCase) {
        this.storage = storage;
        this.getPaymentUseCase = getPaymentUseCase;
        this.getOrderUseCase = getOrderUseCase;
    }

    /**
     * Uploads the receipt and returns the opaque name to send back on
     * {@code PATCH /api/payments/{id}/proof}. Two steps, because the buyer picks the file before
     * the payment row is even looked up.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("reference", storage.store(file));
    }

    /** Streams the receipt of one payment to whoever is entitled to see it. */
    @GetMapping("/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> file(@PathVariable UUID paymentId,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PaymentDto payment = getPaymentUseCase.execute(paymentId);
        if (currentUser.role() == UserRole.CUSTOMER) {
            var order = getOrderUseCase.execute(payment.orderId());
            if (!order.customerId().equals(currentUser.id())) {
                throw new AccessDeniedException("You can only open the receipt of your own payments");
            }
        }
        String reference = payment.proofReference();
        if (!storage.isStoredFile(reference)) {
            throw new DomainException("This payment's proof is an external link, not a stored file");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, storage.contentTypeOf(reference))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprobante-" + paymentId + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(storage.resolve(reference)));
    }
}
