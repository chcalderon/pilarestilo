package com.pilarestilo.product.domain.ports;

import com.pilarestilo.product.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    /** Every product in the given set, in one query. Missing ids are simply absent. */
    List<Product> findAllByIds(Collection<UUID> ids);

    Page<Product> findAll(ProductFilter filter, Pageable pageable);

    void deleteById(UUID id);

    void updateRatingSummary(UUID productId, BigDecimal avgRating, int reviewCount);

    int atomicReserveVariantStock(UUID productId, String color, String size, int qty);

    /**
     * Recomputes the product's aggregate stock from its variant rows.
     *
     * <p>Call after any variant movement. The aggregate is a cache of what the variants already
     * say, and the product page refuses to sell a product whose aggregate reads zero.
     */
    int syncProductStockFromVariants(UUID productId);

    int atomicReleaseVariantStock(UUID productId, String color, String size, int qty);

    int atomicConfirmVariantStock(UUID productId, String color, String size, int qty);

    /** Puts confirmed units back on the shelf when a paid sale is undone. */
    int atomicReturnVariantStock(UUID productId, String color, String size, int qty);

    Page<Product> search(String term,
                         Boolean active,
                         Boolean inStock,
                         String condition,
                         String categorySlug,
                         LocalDate createdFrom,
                         LocalDate createdTo,
                         Pageable pageable);

    record ProductFilter(
            String condition,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            Boolean inStock,
            String categorySlug,
            LocalDate createdFrom,
            LocalDate createdTo
    ) {
        public static ProductFilter empty() {
            return new ProductFilter(null, null, null, null, null, null, null, null, null);
        }
    }
}
