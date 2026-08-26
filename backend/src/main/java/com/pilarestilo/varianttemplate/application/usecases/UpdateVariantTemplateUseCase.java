package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public UpdateVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public VariantTemplateDto execute(UUID id, String name, VariantFieldRequest primary, VariantFieldRequest secondary) {
        VariantTemplate template = variantTemplateRepository.findById(id)
                .orElseThrow(() -> new DomainException("Variant template not found: " + id));
        VariantFieldConfig config = new VariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
        template.update(name, config);
        return VariantTemplateDto.from(variantTemplateRepository.save(template));
    }

    private VariantFieldConfig.FieldConfig toFieldConfig(VariantFieldRequest req) {
        return new VariantFieldConfig.FieldConfig(
                req.label(),
                VariantFieldConfig.InputType.valueOf(req.inputType()),
                req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
    }
}
