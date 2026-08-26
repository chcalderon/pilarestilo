package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListVariantTemplatesUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public ListVariantTemplatesUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<VariantTemplateDto> execute() {
        return variantTemplateRepository.findAll().stream().map(VariantTemplateDto::from).toList();
    }
}
