package com.pilarestilo.product.domain.ports;

import com.pilarestilo.product.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    Page<Product> findAll(ProductFilter filter, Pageable pageable);

    void deleteById(UUID id);

    void updateRatingSummary(UUID productId, BigDecimal avgRating, int reviewCount);

    Page<Product> search(String term,
                         Boolean active,
                         Boolean inStock,
                         String condition,
                         String categorySlug,
                         Pageable pageable);

    record ProductFilter(
            String condition,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            Boolean inStock,
            String categorySlug
    ) {
        public static ProductFilter empty() {
            return new ProductFilter(null, null, null, null, null, null, null);
        }
    }
}
