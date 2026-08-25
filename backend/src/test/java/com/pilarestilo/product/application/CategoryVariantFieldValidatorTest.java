package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryVariantFieldValidatorTest {

    @Mock
    CategoryRepository categoryRepository;

    private final CategoryVariantFieldConfig.FieldConfig freeText =
            new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                    List.of(), null, null, false, true);
    private final CategoryVariantFieldConfig.FieldConfig sizeOptions =
            new CategoryVariantFieldConfig.FieldConfig("Talla", CategoryVariantFieldConfig.InputType.OPTIONS,
                    List.of("S", "M", "L"), null, null, true, false);
    private final CategoryVariantFieldConfig.FieldConfig shoeRange =
            new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                    List.of(), 34, 43, true, false);

    @Test
    void resolveConfig_noCategoryIds_returnsGenericFallback() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);

        CategoryVariantFieldConfig config = validator.resolveConfig(Set.of());

        assertEquals(CategoryVariantFieldConfig.genericFallback(), config);
    }

    @Test
    void resolveConfig_oneShapeCategory_returnsItsConfig() {
        Category zapatos = shapeCategory("zapatos", new CategoryVariantFieldConfig(freeText, shoeRange));
        UUID id = UUID.randomUUID();
        when(categoryRepository.findAllByIds(Set.of(id))).thenReturn(List.of(zapatos));
        var validator = new CategoryVariantFieldValidator(categoryRepository);

        CategoryVariantFieldConfig config = validator.resolveConfig(Set.of(id));

        assertEquals(shoeRange, config.secondary());
    }

    @Test
    void validate_freeTextField_rejectsOnlyBlank() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, freeText);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "Cualquiera", 1))));
        var blank = List.of(new ProductVariantInput("Negro", "", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, blank));
    }

    @Test
    void validate_optionsField_rejectsValueNotInList_whenCustomNotAllowed() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "M", 1))));
        var outOfList = List.of(new ProductVariantInput("Negro", "XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, outOfList));
    }

    @Test
    void validate_optionsField_multiValue_splitsOnHyphenAndValidatesEachToken() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-M-L", 1))));
        var oneTokenOutOfList = List.of(new ProductVariantInput("Negro", "S-XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, oneTokenOutOfList));
    }

    @Test
    void validate_optionsField_multiValue_rejectsDuplicateToken() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        var duplicateTokens = List.of(new ProductVariantInput("Negro", "S-S", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, duplicateTokens));
    }

    @Test
    void validate_singleValueField_doesNotSplitOnHyphen() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var singleValueFreeText = new CategoryVariantFieldConfig.FieldConfig(
                "Color", CategoryVariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);
        var config = new CategoryVariantFieldConfig(singleValueFreeText, freeText);

        // "Azul-Marino" is one color, not two tokens, because primary.allowMultiple() is false.
        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Azul-Marino", "Cualquiera", 1))));
    }

    @Test
    void validate_rangeField_acceptsWithinBoundsAndRejectsOutside() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, shoeRange);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "38", 1))));
        var aboveRange = List.of(new ProductVariantInput("Blanco", "50", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, aboveRange));
        var notANumber = List.of(new ProductVariantInput("Blanco", "not-a-number", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, notANumber));
    }

    @Test
    void validate_optionsField_allowsCustomValueWhenConfigured() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var customAllowed = new CategoryVariantFieldConfig.FieldConfig(
                "Talla", CategoryVariantFieldConfig.InputType.OPTIONS, List.of("S", "M", "L"), null, null, true, true);
        var config = new CategoryVariantFieldConfig(freeText, customAllowed);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "XXL-a-medida", 1))));
    }

    private static Category shapeCategory(String slug, CategoryVariantFieldConfig config) {
        Category c = Category.create(slug, slug, slug, null, 0, null);
        c.updateVariantFieldConfig(true, config);
        return c;
    }
}
