package com.pilarestilo.cashregister.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * POS channel endpoint stub — Windows POS integration pending.
 * See docs/pos-channel.md for the planned contract.
 */
@RestController
@RequestMapping("/api/pos")
public class PosController {

    /**
     * POST /api/pos/sales
     *
     * Planned request body:
     * {
     *   "items": [...],           // same shape as CreateOrderRequest.items
     *   "paymentMethod": "CASH",  // PaymentMethod enum
     *   "cashRegisterId": "uuid", // optional — if omitted, uses seller's open register
     *   "customerId": "uuid"      // optional — null for walk-in customers
     * }
     *
     * When implemented:
     * - salesChannel forced to POS
     * - if paymentMethod == CASH, calls RegisterPosSaleUseCase
     * - returns created Order
     */
    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Map<String, String> createPosSale() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
            "POS channel not yet implemented. See docs/pos-channel.md");
    }
}
