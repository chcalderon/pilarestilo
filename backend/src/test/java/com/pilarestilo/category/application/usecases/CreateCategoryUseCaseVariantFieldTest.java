package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.infrastructure.web.requests.CategoryVariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCategoryUseCaseVariantFieldTest {

    @Mock
    CategoryRepository categoryRepository;

    @Test
    void create_withVariantFieldConfig_persistsIt() {
        CreateCategoryUseCase useCase = new CreateCategoryUseCase(categoryRepository);
        when(categoryRepository.existsBySlug("zapatos")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        var primary = new CategoryVariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new CategoryVariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        CategoryDto dto = useCase.execute(
                "zapatos", "Zapatos", "Shoes", null, 5, null,
                true, false, true, "GENERIC", null,
                true, primary, secondary
        );

        assertTrue(dto.definesVariantFields());
        assertNotNull(dto.variantFieldConfig());
    }

    @Test
    void create_withoutDefiningVariantFields_leavesConfigNull() {
        CreateCategoryUseCase useCase = new CreateCategoryUseCase(categoryRepository);
        when(categoryRepository.existsBySlug("mujer")).thenReturn(false);
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        when(categoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute("mujer", "Mujer", "Women", null, 0, null,
                true, false, true, "GENERIC", null,
                false, null, null);

        assertFalse(captor.getValue().isDefinesVariantFields());
        assertNull(captor.getValue().getVariantFieldConfig());
    }
}
