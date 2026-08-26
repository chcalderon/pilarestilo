package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public DeleteVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public void execute(UUID id) {
        if (variantTemplateRepository.findById(id).isEmpty()) {
            throw new DomainException("Variant template not found: " + id);
        }
        if (variantTemplateRepository.hasAssociatedProducts(id)) {
            throw new DomainException("Cannot delete variant template with associated products. Reassign products first.");
        }
        variantTemplateRepository.deleteById(id);
    }
}
