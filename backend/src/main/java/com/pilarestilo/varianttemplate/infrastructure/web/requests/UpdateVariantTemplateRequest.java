package com.pilarestilo.varianttemplate.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantTemplateRequest(
        @NotBlank String name,
        @NotNull @Valid VariantFieldRequest primary,
        @NotNull @Valid VariantFieldRequest secondary
) {}
