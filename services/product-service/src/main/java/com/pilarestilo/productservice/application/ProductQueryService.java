package com.pilarestilo.productservice.application;

import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
                                    Boolean inStock,
                                    String category,
                                    LocalDate createdFrom,
                                    LocalDate createdTo,
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
            if (Boolean.TRUE.equals(inStock)) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (category != null && !category.isBlank()) {
                Join<Object, Object> categories = root.join("categories", jakarta.persistence.criteria.JoinType.INNER);
                predicates.add(cb.equal(categories.get("slug"), category));
                if (query != null) {
                    query.distinct(true);
                }
            }
            appendCreatedAtPredicates(predicates, root, cb, createdFrom, createdTo);

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
    public Page<ProductEntity> search(String queryText,
                                      Boolean active,
                                      Boolean inStock,
                                      String condition,
                                      String category,
                                      LocalDate createdFrom,
                                      LocalDate createdTo,
                                      Pageable pageable) {
        String term = queryText == null ? "" : queryText.trim().toLowerCase();
        Specification<ProductEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (Boolean.TRUE.equals(inStock)) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (!term.isBlank()) {
                String pattern = "%" + term + "%";
                Join<Object, Object> textCats = root.join("categories", JoinType.LEFT);
                var namePredicate = cb.like(cb.lower(root.get("name")), pattern);
                var brandPredicate = cb.like(cb.lower(root.get("brand")), pattern);
                var descPredicate = cb.like(cb.lower(root.get("description")), pattern);
                var catEsPredicate = cb.like(cb.lower(textCats.get("nameEs")), pattern);
                var catEnPredicate = cb.like(cb.lower(textCats.get("nameEn")), pattern);
                var catSlugPredicate = cb.like(cb.lower(textCats.get("slug")), pattern);
                predicates.add(cb.or(namePredicate, brandPredicate, descPredicate, catEsPredicate, catEnPredicate, catSlugPredicate));
            }
            if (condition != null && !condition.isBlank()) {
                predicates.add(cb.equal(root.get("condition"), condition));
            }
            if (category != null && !category.isBlank()) {
                Join<Object, Object> filterCats = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(filterCats.get("slug"), category));
            }
            appendCreatedAtPredicates(predicates, root, cb, createdFrom, createdTo);
            if (query != null) {
                query.distinct(true);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, pageable);
    }

    private void appendCreatedAtPredicates(ArrayList<Predicate> predicates,
                                           jakarta.persistence.criteria.Root<ProductEntity> root,
                                           jakarta.persistence.criteria.CriteriaBuilder cb,
                                           LocalDate createdFrom,
                                           LocalDate createdTo) {
        if (createdFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    createdFrom.atStartOfDay().toInstant(ZoneOffset.UTC)
            ));
        }
        if (createdTo != null) {
            predicates.add(cb.lessThan(
                    root.get("createdAt"),
                    createdTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            ));
        }
    }

    private Predicate buildInStockPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                            jakarta.persistence.criteria.CriteriaQuery<?> query,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        /*
         * Variants only, which is what the monolith answers and what the shop actually decrements.
         *
         * The sizeStocks join stayed here after V56 moved stock onto product_variants, and nothing
         * has written that table since. It held 24 units that no sale could ever reduce, so this
         * service reported ten sold-out garments as available while the storefront's own SSR, which
         * asks the monolith, showed none. In production Caddy routes GET /api/products here, so the
         * two halves of the same page disagreed about what could be bought.
         */
        Join<Object, Object> variants = root.join("variants", JoinType.LEFT);
        if (query != null) {
            query.distinct(true);
        }
        return cb.or(
                cb.greaterThan(root.get("stock"), 0),
                cb.greaterThan(variants.get("stockOnHand"), 0)
        );
    }
}
