package com.pilarestilo.returns.domain.enums;

/**
 * How the money goes back. The rule is that it returns by the same means it arrived, so this mirrors
 * how the sale was paid rather than offering a choice.
 *
 * <p>{@link #TRANSFERENCIA} is the only one that needs the customer's bank details, which is why
 * they are asked for when the return is opened and not before.
 */
public enum RefundMethod {
    TRANSFERENCIA,
    TARJETA,
    OTRO
}
