package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void create_persistsTemplateWithGivenConfig() {
        when(variantTemplateRepository.save(any(VariantTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        var useCase = new CreateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        VariantTemplateDto dto = useCase.execute("Zapatos", primary, secondary);

        assertEquals("Zapatos", dto.name());
        assertEquals("Numero", dto.config().secondary().label());
        assertEquals(34, dto.config().secondary().min());
    }
}
