package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseVariantValidationTest {

    @Mock ProductRepository productRepository;
    @Mock DomainEventPublisher eventPublisher;
    @Mock CategoryRepository categoryRepository;

    @Test
    void rejects_variantValue_notAllowedByShapeCategoryConfig() {
        UUID shoesId = UUID.randomUUID();
        Category zapatos = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        zapatos.updateVariantFieldConfig(true, new CategoryVariantFieldConfig(
                new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(categoryRepository.findAllByIds(Set.of(shoesId))).thenReturn(List.of(zapatos));

        CategoryVariantFieldValidator validator = new CategoryVariantFieldValidator(categoryRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        var outOfRangeVariant = List.of(new ProductVariantInput("Blanco", "50", 1));
        assertThrows(DomainException.class, () -> useCase.execute(
                "Zapato", "desc", BigDecimal.valueOf(50000), "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, Set.of(shoesId),
                outOfRangeVariant
        ));
    }

    @Test
    void accepts_variantValue_withinShapeCategoryConfig() {
        UUID shoesId = UUID.randomUUID();
        Category zapatos = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        zapatos.updateVariantFieldConfig(true, new CategoryVariantFieldConfig(
                new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(categoryRepository.findAllByIds(Set.of(shoesId))).thenReturn(List.of(zapatos));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryVariantFieldValidator validator = new CategoryVariantFieldValidator(categoryRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        ProductDto dto = useCase.execute(
                "Zapato", "desc", BigDecimal.valueOf(50000), "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, Set.of(shoesId),
                List.of(new ProductVariantInput("Blanco", "38", 1))
        );

        assertNotNull(dto.id());
    }
}
