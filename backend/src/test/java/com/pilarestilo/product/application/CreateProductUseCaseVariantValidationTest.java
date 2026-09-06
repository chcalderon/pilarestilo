package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseVariantValidationTest {

    @Mock ProductRepository productRepository;
    @Mock DomainEventPublisher eventPublisher;
    @Mock VariantTemplateRepository variantTemplateRepository;

    @Test
    void rejects_variantValue_notAllowedByTemplateConfig() {
        UUID templateId = UUID.randomUUID();
        VariantTemplate zapatos = VariantTemplate.create("Zapatos", new VariantFieldConfig(
                new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(variantTemplateRepository.findById(templateId)).thenReturn(Optional.of(zapatos));

        VariantTemplateValidator validator = new VariantTemplateValidator(variantTemplateRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        var outOfRangeVariant = List.of(new ProductVariantInput("Blanco", "50", 1));
        var price = BigDecimal.valueOf(50000);
        assertThrows(DomainException.class, () -> useCase.execute(
                "Zapato", "desc", price, "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, null, templateId,
                outOfRangeVariant, List.of()
        ));
    }

    @Test
    void accepts_variantValue_withinTemplateConfig() {
        UUID templateId = UUID.randomUUID();
        VariantTemplate zapatos = VariantTemplate.create("Zapatos", new VariantFieldConfig(
                new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(variantTemplateRepository.findById(templateId)).thenReturn(Optional.of(zapatos));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        VariantTemplateValidator validator = new VariantTemplateValidator(variantTemplateRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        ProductDto dto = useCase.execute(
                "Zapato", "desc", BigDecimal.valueOf(50000), "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, null, templateId,
                List.of(new ProductVariantInput("Blanco", "38", 1)), List.of()
        );

        assertNotNull(dto.id());
    }
}
