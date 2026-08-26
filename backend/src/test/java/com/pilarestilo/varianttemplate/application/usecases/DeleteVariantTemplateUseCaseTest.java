package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void delete_removesTemplateWithNoAssociatedProducts() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.hasAssociatedProducts(id)).thenReturn(false);
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        useCase.execute(id);

        verify(variantTemplateRepository).deleteById(id);
    }

    @Test
    void delete_rejectsWhenTemplateHasAssociatedProducts() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.hasAssociatedProducts(id)).thenReturn(true);
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        assertThrows(DomainException.class, () -> useCase.execute(id));
    }

    @Test
    void delete_throwsWhenTemplateNotFound() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        assertThrows(DomainException.class, () -> useCase.execute(id));
    }
}
