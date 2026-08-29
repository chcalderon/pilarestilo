package com.pilarestilo.notificationservice.shared;

/** An expected domain-level failure. Unchecked, same as the monolith's. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
