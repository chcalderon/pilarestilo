package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void update_replacesNameAndConfig() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.save(any(VariantTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        var useCase = new UpdateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        VariantTemplateDto dto = useCase.execute(id, "Zapatos Deportivos", primary, secondary);

        assertEquals("Zapatos Deportivos", dto.name());
        assertEquals("Numero", dto.config().secondary().label());
    }

    @Test
    void update_throwsWhenTemplateNotFound() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var useCase = new UpdateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        assertThrows(DomainException.class, () -> useCase.execute(id, "Zapatos", primary, secondary));
    }
}
