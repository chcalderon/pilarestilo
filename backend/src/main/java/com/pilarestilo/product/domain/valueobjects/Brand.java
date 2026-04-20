package com.pilarestilo.product.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;

import java.util.Objects;

public record Brand(String value) {

    public Brand {
        Objects.requireNonNull(value, "Brand value cannot be null");
        if (value.isBlank()) {
            throw new DomainException("Brand value cannot be blank");
        }
    }
}
