package com.pilarestilo.productai.infrastructure.web.requests;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateProductAiDraftRequest(
        String name,
        String brand,
        String condition,
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal priceAmount,
        String priceCurrency
) {
    public String normalizedName() {
        return name == null || name.isBlank() ? "Producto IA borrador" : name.trim();
    }

    public String normalizedBrand() {
        return brand == null || brand.isBlank() ? "Sin marca" : brand.trim();
    }

    public String normalizedCondition() {
        return condition == null || condition.isBlank() ? "USED" : condition.trim().toUpperCase();
    }

    public String normalizedCurrency() {
        return priceCurrency == null || priceCurrency.isBlank() ? "CLP" : priceCurrency.trim().toUpperCase();
    }
}
