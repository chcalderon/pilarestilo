package com.pilarestilo.returns.domain.enums;

/**
 * Where the garment stands.
 *
 * <p>Every returned garment is cleaned, pressed, sanitised and repaired before it can be sold again,
 * so arriving is not the same as being back on the shelf. {@link #PENDING_RECONDITIONING} is where a
 * garment spends most of its time, and a boolean could not express it — which is why this is an enum
 * and why receiving a return touches no stock at all.
 */
public enum ItemDisposition {
    PENDING_RECONDITIONING,
    RESTOCKED,
    DISCARDED
}
