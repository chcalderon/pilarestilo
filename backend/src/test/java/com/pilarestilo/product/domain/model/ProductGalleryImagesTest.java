package com.pilarestilo.product.domain.model;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductGalleryImagesTest {

    private Product newProduct() {
        return Product.create("Abrigo", "d", new Money(BigDecimal.valueOf(29990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
    }

    @Test
    void defaults_to_an_empty_list() {
        assertEquals(List.of(), newProduct().getGalleryImageUrls());
    }

    @Test
    void null_clears_to_empty() {
        Product p = newProduct();
        p.setGalleryImageUrls(List.of("https://img/a.jpg"));
        p.setGalleryImageUrls(null);
        assertEquals(List.of(), p.getGalleryImageUrls());
    }

    @Test
    void trims_drops_blanks_and_dedupes_preserving_order() {
        Product p = newProduct();
        p.setGalleryImageUrls(new ArrayList<>(List.of(
                "  https://img/a.jpg  ", "", "   ", "https://img/b.jpg", "https://img/a.jpg")));
        assertEquals(List.of("https://img/a.jpg", "https://img/b.jpg"), p.getGalleryImageUrls());
    }

    @Test
    void caps_at_nine_keeping_the_first_nine() {
        Product p = newProduct();
        List<String> twelve = IntStream.range(0, 12).mapToObj(i -> "https://img/" + i + ".jpg").toList();
        p.setGalleryImageUrls(twelve);
        assertEquals(twelve.subList(0, 9), p.getGalleryImageUrls());
    }

    @Test
    void getter_returns_a_copy() {
        Product p = newProduct();
        p.setGalleryImageUrls(List.of("https://img/a.jpg"));
        assertThrows(UnsupportedOperationException.class,
                () -> p.getGalleryImageUrls().add("https://img/x.jpg"));
    }
}
