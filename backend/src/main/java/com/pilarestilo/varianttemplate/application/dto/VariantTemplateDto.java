package com.pilarestilo.varianttemplate.application.dto;

import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.util.List;
import java.util.UUID;

public record VariantTemplateDto(UUID id, String name, VariantFieldConfigDto config) {

    public static VariantTemplateDto from(VariantTemplate t) {
        return new VariantTemplateDto(t.getId(), t.getName(), VariantFieldConfigDto.from(t.getConfig()));
    }

    public record VariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        public static VariantFieldConfigDto from(VariantFieldConfig config) {
            return new VariantFieldConfigDto(FieldDto.from(config.primary()), FieldDto.from(config.secondary()));
        }

        public record FieldDto(String label, String inputType, List<String> options, Integer min, Integer max,
                                boolean allowMultiple, boolean allowCustom) {
            public static FieldDto from(VariantFieldConfig.FieldConfig field) {
                return new FieldDto(field.label(), field.inputType().name(), field.options(),
                        field.min(), field.max(), field.allowMultiple(), field.allowCustom());
            }
        }
    }
}
