package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantTemplateValidatorTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    private final VariantFieldConfig.FieldConfig freeText =
            new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT,
                    List.of(), null, null, false, true);
    private final VariantFieldConfig.FieldConfig sizeOptions =
            new VariantFieldConfig.FieldConfig("Talla", VariantFieldConfig.InputType.OPTIONS,
                    List.of("S", "M", "L"), null, null, true, false);
    private final VariantFieldConfig.FieldConfig shoeRange =
            new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE,
                    List.of(), 34, 43, true, false);

    @Test
    void resolveConfig_nullTemplateId_returnsGenericFallback() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        VariantFieldConfig config = validator.resolveConfig(null);

        assertEquals(VariantFieldConfig.genericFallback(), config);
    }

    @Test
    void resolveConfig_knownTemplateId_returnsItsConfig() {
        UUID id = UUID.randomUUID();
        VariantTemplate template = VariantTemplate.create("Zapatos", new VariantFieldConfig(freeText, shoeRange));
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(template));
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        VariantFieldConfig config = validator.resolveConfig(id);

        assertEquals(shoeRange, config.secondary());
    }

    @Test
    void resolveConfig_unknownTemplateId_throws() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        assertThrows(DomainException.class, () -> validator.resolveConfig(id));
    }

    @Test
    void validate_freeTextField_rejectsOnlyBlank() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, freeText);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "Cualquiera", 1))));
        var blank = List.of(new ProductVariantInput("Negro", "", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, blank));
    }

    @Test
    void validate_optionsField_rejectsValueNotInList_whenCustomNotAllowed() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "M", 1))));
        var outOfList = List.of(new ProductVariantInput("Negro", "XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, outOfList));
    }

    @Test
    void validate_optionsField_multiValue_splitsOnHyphenAndValidatesEachToken() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-M-L", 1))));
        var oneTokenOutOfList = List.of(new ProductVariantInput("Negro", "S-XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, oneTokenOutOfList));
    }

    @Test
    void validate_optionsField_multiValue_rejectsDuplicateToken() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        var duplicateTokens = List.of(new ProductVariantInput("Negro", "S-S", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, duplicateTokens));
    }

    @Test
    void validate_singleValueField_doesNotSplitOnHyphen() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var singleValueFreeText = new VariantFieldConfig.FieldConfig(
                "Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);
        var config = new VariantFieldConfig(singleValueFreeText, freeText);

        // "Azul-Marino" is one color, not two tokens, because primary.allowMultiple() is false.
        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Azul-Marino", "Cualquiera", 1))));
    }

    @Test
    void validate_rangeField_acceptsWithinBoundsAndRejectsOutside() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, shoeRange);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "38", 1))));
        var aboveRange = List.of(new ProductVariantInput("Blanco", "50", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, aboveRange));
        var notANumber = List.of(new ProductVariantInput("Blanco", "not-a-number", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, notANumber));
    }

    @Test
    void validate_optionsField_allowsCustomValueWhenConfigured() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var customAllowed = new VariantFieldConfig.FieldConfig(
                "Talla", VariantFieldConfig.InputType.OPTIONS, List.of("S", "M", "L"), null, null, true, true);
        var config = new VariantFieldConfig(freeText, customAllowed);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "XXL-a-medida", 1))));
    }
}
