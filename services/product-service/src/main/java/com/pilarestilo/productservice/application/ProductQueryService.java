package com.pilarestilo.productservice.application;

import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> list(String condition,
                                    String brand,
                                    BigDecimal minPrice,
                                    BigDecimal maxPrice,
                                    Boolean active,
                                    String category,
                                    Pageable pageable) {
        Specification<ProductEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (condition != null && !condition.isBlank()) {
                predicates.add(cb.equal(root.get("condition"), condition));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("brand")), "%" + brand.toLowerCase() + "%"));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceAmount"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceAmount"), maxPrice));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (category != null && !category.isBlank()) {
                Join<Object, Object> categories = root.join("categories", jakarta.persistence.criteria.JoinType.INNER);
                predicates.add(cb.equal(categories.get("slug"), category));
                if (query != null) {
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return productRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public ProductEntity getById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> search(String queryText, Pageable pageable) {
        String term = queryText == null ? "" : queryText.trim().toLowerCase();
        if (term.isBlank()) {
            return productRepository.findAll(pageable);
        }

        String pattern = "%" + term + "%";
        Specification<ProductEntity> spec = (root, query, cb) -> {
            var namePredicate = cb.like(cb.lower(root.get("name")), pattern);
            var brandPredicate = cb.like(cb.lower(root.get("brand")), pattern);
            if (query != null) {
                query.distinct(true);
            }
            return cb.or(namePredicate, brandPredicate);
        };
        return productRepository.findAll(spec, pageable);
    }
}
