package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.navigation.application.dto.NavigationTreeDto;
import com.pilarestilo.navigation.domain.enums.NavigationLayout;
import com.pilarestilo.navigation.domain.model.NavigationSection;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetNavigationTreeUseCaseTest {

    @Mock
    private NavigationSectionRepository navigationSectionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private GetNavigationTreeUseCase useCase;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build an active root category (no parent). */
    private Category rootCategory(UUID id, String slug, String nameEs, String nameEn) {
        Category c = Category.create(slug, nameEs, nameEn, null, 0, null);
        c.setId(id);
        return c;
    }

    /** Build an active child category under parentId, with menuVisible=true. */
    private Category childCategory(UUID id, UUID parentId, String slug, String nameEs, String nameEn, int sortOrder) {
        Category c = Category.create(slug, nameEs, nameEn, parentId, sortOrder, null);
        c.setId(id);
        return c;
    }

    /** Build a minimal active NavigationSection pointing at rootCategoryId. */
    private NavigationSection section(UUID rootCategoryId) {
        return NavigationSection.reconstruct(
                UUID.randomUUID(), rootCategoryId, NavigationLayout.COLUMNS, 4,
                null, null, null, null, true, 0,
                Instant.now(), Instant.now()
        );
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void execute_localeEs_returnsSpanishNames() {
        UUID rootId = UUID.randomUUID();
        Category root = rootCategory(rootId, "ropa", "Ropa", "Clothing");

        when(categoryRepository.findAll()).thenReturn(List.of(root));
        when(navigationSectionRepository.findAllActiveOrdered()).thenReturn(List.of(section(rootId)));

        NavigationTreeDto result = useCase.execute("es");

        assertEquals(1, result.sections().size());
        assertEquals("Ropa", result.sections().get(0).rootCategoryName());
    }

    @Test
    void execute_localeEn_returnsEnglishNames() {
        UUID rootId = UUID.randomUUID();
        Category root = rootCategory(rootId, "ropa", "Ropa", "Clothing");

        when(categoryRepository.findAll()).thenReturn(List.of(root));
        when(navigationSectionRepository.findAllActiveOrdered()).thenReturn(List.of(section(rootId)));

        NavigationTreeDto result = useCase.execute("en");

        assertEquals(1, result.sections().size());
        assertEquals("Clothing", result.sections().get(0).rootCategoryName());
    }

    @Test
    void execute_fallsBackToEs_whenEnNameBlank() {
        UUID rootId = UUID.randomUUID();
        // Use a spy so we can override getNameEn() to return blank
        Category root = spy(rootCategory(rootId, "accesorios", "Accesorios", "Accessories"));
        doReturn("").when(root).getNameEn();

        when(categoryRepository.findAll()).thenReturn(List.of(root));
        when(navigationSectionRepository.findAllActiveOrdered()).thenReturn(List.of(section(rootId)));

        NavigationTreeDto result = useCase.execute("en");

        assertEquals(1, result.sections().size());
        // Should fall back to the Spanish name
        assertEquals("Accesorios", result.sections().get(0).rootCategoryName());
    }

    @Test
    void execute_excludesNonMenuVisibleChildCategories() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Category root = rootCategory(rootId, "ropa", "Ropa", "Clothing");
        Category child = childCategory(childId, rootId, "camisas", "Camisas", "Shirts", 1);
        child.setMenuVisible(false);  // hidden from menu

        when(categoryRepository.findAll()).thenReturn(List.of(root, child));
        when(navigationSectionRepository.findAllActiveOrdered()).thenReturn(List.of(section(rootId)));

        NavigationTreeDto result = useCase.execute("es");

        assertEquals(1, result.sections().size());
        // The hidden child should not appear
        assertTrue(result.sections().get(0).children().isEmpty(),
                "Children with menuVisible=false should be excluded");
    }

    @Test
    void execute_returnsEmptySections_whenNoActiveSections() {
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(navigationSectionRepository.findAllActiveOrdered()).thenReturn(List.of());

        NavigationTreeDto result = useCase.execute("es");

        assertNotNull(result);
        assertTrue(result.sections().isEmpty());
    }
}
