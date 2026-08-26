package com.pilarestilo.varianttemplate.infrastructure.persistence.repositories;

import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class VariantTemplateRepositoryAdapter implements VariantTemplateRepository {

    private final VariantTemplateJpaRepository jpa;
    private final ProductJpaRepository productJpa;

    public VariantTemplateRepositoryAdapter(VariantTemplateJpaRepository jpa, ProductJpaRepository productJpa) {
        this.jpa = jpa;
        this.productJpa = productJpa;
    }

    @Override
    public VariantTemplate save(VariantTemplate template) {
        VariantTemplateEntity e = toEntity(template);
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<VariantTemplate> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<VariantTemplate> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean hasAssociatedProducts(UUID templateId) {
        return productJpa.countByVariantTemplateId(templateId) > 0;
    }

    private VariantTemplateEntity toEntity(VariantTemplate t) {
        VariantTemplateEntity e = new VariantTemplateEntity();
        e.setId(t.getId());
        e.setName(t.getName());
        e.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now());
        e.setFieldConfig(toRawConfig(t.getConfig()));
        return e;
    }

    private VariantTemplate toDomain(VariantTemplateEntity e) {
        VariantTemplate t = VariantTemplate.create(e.getName(), fromRawConfig(e.getFieldConfig()));
        t.setId(e.getId());
        t.setCreatedAt(e.getCreatedAt());
        return t;
    }

    private static final String OPTIONS_KEY = "options";

    private static Map<String, Object> toRawConfig(VariantFieldConfig config) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("primary", toRawField(config.primary()));
        raw.put("secondary", toRawField(config.secondary()));
        return raw;
    }

    private static Map<String, Object> toRawField(VariantFieldConfig.FieldConfig field) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("label", field.label());
        raw.put("inputType", field.inputType().name());
        raw.put(OPTIONS_KEY, field.options());
        raw.put("min", field.min());
        raw.put("max", field.max());
        raw.put("allowMultiple", field.allowMultiple());
        raw.put("allowCustom", field.allowCustom());
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig fromRawConfig(Map<String, Object> raw) {
        return new VariantFieldConfig(
                fromRawField((Map<String, Object>) raw.get("primary")),
                fromRawField((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
        List<String> options = raw.get(OPTIONS_KEY) == null
                ? List.of()
                : ((List<Object>) raw.get(OPTIONS_KEY)).stream().map(String::valueOf).toList();
        return new VariantFieldConfig.FieldConfig(
                (String) raw.get("label"),
                VariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
