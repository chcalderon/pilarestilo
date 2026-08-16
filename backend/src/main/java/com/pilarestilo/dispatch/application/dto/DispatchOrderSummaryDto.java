package com.pilarestilo.dispatch.application.dto;

import java.math.BigDecimal;

/**
 * What a person needs to recognise an order in the dispatch queue.
 *
 * <p>The queue identified an order by the first eight characters of its UUID, which tells whoever
 * is packing nothing at all: not which garment, not how much, not which order this is when they
 * have the reference in hand. Every field here exists to answer one of those.
 *
 * <p>Carried as one nested value rather than six more columns on DispatchDto, which is already a
 * nineteen-argument positional record.
 *
 * @param publicReference   the reference the customer and the bank statement both use (PE-XXXXXXXXXX)
 * @param itemCount         how many lines the order has, so a multi-item parcel is obvious
 * @param firstItemName     the garment, or the first of several
 * @param firstItemVariant  colour and size of that garment, already formatted, null when it has none
 * @param firstItemImageUrl thumbnail of that garment, null when the product has no image
 * @param totalAmount       order total
 * @param currency          currency of the total
 */
public record DispatchOrderSummaryDto(
        String publicReference,
        int itemCount,
        String firstItemName,
        String firstItemVariant,
        String firstItemImageUrl,
        BigDecimal totalAmount,
        String currency
) {}
