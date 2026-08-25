package com.pilarestilo.category.domain.model;

import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryVariantFieldConfigMutatorTest {

    @Test
    void category_defaultsToNotDefiningVariantFields() {
        Category c = Category.create("mujer", "Mujer", "Women", null, 0, null);

        assertFalse(c.isDefinesVariantFields());
        assertNull(c.getVariantFieldConfig());
    }

    @Test
    void updateVariantFieldConfig_setsBothFields() {
        Category c = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        CategoryVariantFieldConfig config = CategoryVariantFieldConfig.genericFallback();

        c.updateVariantFieldConfig(true, config);

        assertTrue(c.isDefinesVariantFields());
        assertEquals(config, c.getVariantFieldConfig());
    }

    @Test
    void updateVariantFieldConfig_falseClearsConfigEvenIfProvided() {
        Category c = Category.create("mujer", "Mujer", "Women", null, 0, null);

        c.updateVariantFieldConfig(false, CategoryVariantFieldConfig.genericFallback());

        assertFalse(c.isDefinesVariantFields());
        assertNull(c.getVariantFieldConfig());
    }
}
