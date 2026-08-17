package com.pilarestilo.returns.domain.enums;

/**
 * Who is undoing the sale, and whether the shop may say no.
 *
 * <p>{@link #RETRACTO} is the customer exercising art. 3 bis of the Ley 19.496 within her window.
 * It is a right, not a request: once validly opened it cannot be refused.
 *
 * <p>{@link #DEVOLUCION} is everything else — the shop taking a garment back by agreement, outside
 * the retracto window or on its own initiative. That one can be rejected, with a reason.
 */
public enum ReturnKind {
    RETRACTO,
    DEVOLUCION
}
