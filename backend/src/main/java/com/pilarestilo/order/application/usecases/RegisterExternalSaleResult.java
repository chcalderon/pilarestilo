package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;

/**
 * @param dto      the created (or, on a replay, the existing) order
 * @param replayed true when an idempotency-key match returned an existing order without creating one
 */
public record RegisterExternalSaleResult(OrderDto dto, boolean replayed) {}
