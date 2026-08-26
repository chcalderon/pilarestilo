package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public CreateVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public VariantTemplateDto execute(String name, VariantFieldRequest primary, VariantFieldRequest secondary) {
        VariantFieldConfig config = new VariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
        VariantTemplate template = VariantTemplate.create(name, config);
        return VariantTemplateDto.from(variantTemplateRepository.save(template));
    }

    private VariantFieldConfig.FieldConfig toFieldConfig(VariantFieldRequest req) {
        return new VariantFieldConfig.FieldConfig(
                req.label(),
                VariantFieldConfig.InputType.valueOf(req.inputType()),
                req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
    }
}
