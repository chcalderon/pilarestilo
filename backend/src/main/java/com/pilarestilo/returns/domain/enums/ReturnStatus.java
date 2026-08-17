package com.pilarestilo.returns.domain.enums;

/**
 * Where the money stands. Deliberately not where the garment stands — that is
 * {@link ItemDisposition}, and they move on different clocks: the refund has forty-five days by law,
 * the garment takes as long as reconditioning takes.
 */
public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    RECEIVED,
    REFUNDED,
    REJECTED
}
