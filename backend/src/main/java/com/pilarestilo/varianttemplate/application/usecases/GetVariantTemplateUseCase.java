package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public GetVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional(readOnly = true)
    public VariantTemplateDto execute(UUID id) {
        return variantTemplateRepository.findById(id)
                .map(VariantTemplateDto::from)
                .orElseThrow(() -> new DomainException("Variant template not found: " + id));
    }
}
