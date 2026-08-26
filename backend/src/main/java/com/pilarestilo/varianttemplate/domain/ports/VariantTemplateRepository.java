package com.pilarestilo.varianttemplate.domain.ports;

import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantTemplateRepository {

    VariantTemplate save(VariantTemplate template);

    Optional<VariantTemplate> findById(UUID id);

    List<VariantTemplate> findAll();

    void deleteById(UUID id);

    boolean hasAssociatedProducts(UUID templateId);
}
