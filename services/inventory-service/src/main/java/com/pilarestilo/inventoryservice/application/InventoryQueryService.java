package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

@Service
public class InventoryQueryService {

    private final ProductRepository productRepository;

    public InventoryQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductEntity> list(String condition,
                                    String brand,
                                    Boolean active,
                                    String category,
                                    String q,
                                    Pageable pageable) {
        Specification<ProductEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (condition != null && !condition.isBlank()) {
                predicates.add(cb.equal(root.get("condition"), condition));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("brand")), "%" + brand.toLowerCase() + "%"));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("brand")), pattern)
                ));
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

    public ProductEntity getById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Product not found: " + id));
    }
}
