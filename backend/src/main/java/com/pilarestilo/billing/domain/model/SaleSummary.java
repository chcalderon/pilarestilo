package com.pilarestilo.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the administrative sales list: the order, who bought it, how it was paid and whether
 * its tax document exists.
 *
 * <p>A read model, not an aggregate. The admin had no sales screen at all — payments showed raw
 * UUIDs with no name, amount or products, and the only order detail was six lines inside a dispatch
 * modal — so the shop could approve money without ever seeing what it was for. Assembling this from
 * four separate endpoints per row is what makes such a screen slow enough to be abandoned, so it
 * comes from one query.
 */
public record SaleSummary(
        UUID orderId,
        String publicReference,
        Instant createdAt,
        String orderStatus,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        String currency,
        String paymentMethod,
        String paymentStatus,
        UUID documentId,
        String documentFolio,
        int itemCount,
        String firstItemName
) {}
