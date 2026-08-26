package com.pilarestilo.varianttemplate.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record VariantFieldRequest(
        @NotBlank String label,
        @NotNull @Pattern(regexp = "FREE_TEXT|OPTIONS|RANGE", message = "invalid inputType") String inputType,
        List<String> options,
        Integer min,
        Integer max,
        boolean allowMultiple,
        boolean allowCustom
) {}
