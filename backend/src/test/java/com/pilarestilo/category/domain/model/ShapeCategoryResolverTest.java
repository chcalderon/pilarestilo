package com.pilarestilo.category.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCategoryResolverTest {

    private Category groupingCategory(String slug) {
        return Category.create(slug, slug, slug, null, 0, null);
    }

    private Category shapeCategory(String slug) {
        Category c = Category.create(slug, slug, slug, null, 0, null);
        c.updateVariantFieldConfig(true,
                com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig.genericFallback());
        return c;
    }

    @Test
    void noShapeCategories_resolvesToEmpty() {
        Optional<Category> result = ShapeCategoryResolver.resolveOne(
                List.of(groupingCategory("mujer"), groupingCategory("verano")));

        assertTrue(result.isEmpty());
    }

    @Test
    void oneShapeCategory_resolvesToIt() {
        Category zapatos = shapeCategory("zapatos");

        Optional<Category> result = ShapeCategoryResolver.resolveOne(
                List.of(groupingCategory("mujer"), zapatos));

        assertEquals(zapatos, result.orElseThrow());
    }

    @Test
    void twoShapeCategories_throwsNamingBoth() {
        Category zapatos = shapeCategory("zapatos");
        Category carteras = shapeCategory("carteras");

        var bothShapes = List.of(zapatos, carteras);
        DomainException ex = assertThrows(DomainException.class,
                () -> ShapeCategoryResolver.resolveOne(bothShapes));

        assertTrue(ex.getMessage().contains("zapatos"));
        assertTrue(ex.getMessage().contains("carteras"));
    }

    @Test
    void emptyCollection_resolvesToEmpty() {
        assertTrue(ShapeCategoryResolver.resolveOne(List.of()).isEmpty());
    }
}
