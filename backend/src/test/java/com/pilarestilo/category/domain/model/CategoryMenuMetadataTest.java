package com.pilarestilo.category.domain.model;

import com.pilarestilo.category.domain.enums.CategoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryMenuMetadataTest {

    @Test
    void category_defaultsMenuVisibleTrueAndCategoryTypeGeneric() {
        Category c = Category.create("ropa", "Ropa", "Clothing", null, 0, null);

        assertTrue(c.isMenuVisible(), "menuVisible should default to true");
        assertEquals(CategoryType.GENERIC, c.getCategoryType(), "categoryType should default to GENERIC");
    }

    @Test
    void category_heroImageUrlIsNullableByDefault() {
        Category c = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);

        assertNull(c.getHeroImageUrl(), "heroImageUrl should be null by default");
    }

    @Test
    void updateMenuMetadata_setsFieldsCorrectly() {
        Category c = Category.create("bolsos", "Bolsos", "Bags", null, 0, null);

        c.updateMenuMetadata(false, CategoryType.ACCESSORY, "https://example.com/hero.jpg");

        assertFalse(c.isMenuVisible());
        assertEquals(CategoryType.ACCESSORY, c.getCategoryType());
        assertEquals("https://example.com/hero.jpg", c.getHeroImageUrl());
    }

    @Test
    void updateMenuMetadata_nullCategoryTypeFallsBackToGeneric() {
        Category c = Category.create("novedades", "Novedades", "New", null, 0, null);

        c.updateMenuMetadata(true, null, null);

        assertEquals(CategoryType.GENERIC, c.getCategoryType());
        assertNull(c.getHeroImageUrl());
    }
}
