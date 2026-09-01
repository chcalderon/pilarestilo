package com.pilarestilo.inventory.domain;

import com.pilarestilo.shared.domain.DomainException;

/**
 * A stock line would go short. A subclass of {@link DomainException} so existing callers that
 * catch the parent keep working, but the HTTP layer maps it to 409 rather than 400 — the request
 * was well formed, it just lost a race with the shelf.
 */
public class InsufficientStockException extends DomainException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
