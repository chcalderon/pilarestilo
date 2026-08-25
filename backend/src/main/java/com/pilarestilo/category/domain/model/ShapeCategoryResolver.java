package com.pilarestilo.category.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ShapeCategoryResolver {

    private ShapeCategoryResolver() {}

    /**
     * The one category (if any) among {@code categories} that defines variant fields.
     * Throws when a product ends up tagged with two or more shape categories at once --
     * a data-quality error the admin must fix by picking one, not something to silently
     * disambiguate.
     */
    public static Optional<Category> resolveOne(Collection<Category> categories) {
        List<Category> shapeCategories = categories.stream()
                .filter(Category::isDefinesVariantFields)
                .toList();
        if (shapeCategories.isEmpty()) {
            return Optional.empty();
        }
        if (shapeCategories.size() > 1) {
            String slugs = shapeCategories.stream().map(Category::getSlug).collect(Collectors.joining(", "));
            throw new DomainException(
                    "Product cannot belong to more than one variant-defining category at once: " + slugs);
        }
        return Optional.of(shapeCategories.getFirst());
    }
}
