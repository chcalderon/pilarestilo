package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.enums.ProductSize;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductSizeStock;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductEntity;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductSizeStockEmbeddable;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductVariantEmbeddable;
import com.pilarestilo.shared.application.Money;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository,
                                    CategoryJpaRepository categoryJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = toEntity(product);
        ProductEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<Product> findAll(ProductFilter filter, Pageable pageable) {
        Specification<ProductEntity> spec = buildSpecification(filter);
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void updateRatingSummary(UUID productId, BigDecimal avgRating, int reviewCount) {
        jpaRepository.updateRatingSummary(productId, avgRating, reviewCount);
    }

    @Override
    public Page<Product> search(String term, Boolean active, Boolean inStock, Pageable pageable) {
        String pattern = "%" + term.toLowerCase() + "%";
        Specification<ProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var namePred  = cb.like(cb.lower(root.get("name")),  pattern);
            var brandPred = cb.like(cb.lower(root.get("brand")), pattern);
            predicates.add(cb.or(namePred, brandPred));
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (Boolean.TRUE.equals(inStock)) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (query != null) query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    private Specification<ProductEntity> buildSpecification(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.condition() != null) {
                predicates.add(cb.equal(root.get("condition"),
                        ProductCondition.valueOf(filter.condition())));
            }
            if (filter.brand() != null) {
                predicates.add(cb.like(cb.lower(root.get("brand")),
                        "%" + filter.brand().toLowerCase() + "%"));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceAmount"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceAmount"), filter.maxPrice()));
            }
            if (filter.active() != null) {
                predicates.add(cb.equal(root.get("active"), filter.active()));
            }
            if (Boolean.TRUE.equals(filter.inStock())) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (filter.categorySlug() != null) {
                Join<Object, Object> cats = root.join("categories", jakarta.persistence.criteria.JoinType.INNER);
                predicates.add(cb.equal(cats.get("slug"), filter.categorySlug()));
                if (query != null) query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate buildInStockPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                            jakarta.persistence.criteria.CriteriaQuery<?> query,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Join<Object, Object> variants = root.join("variants", JoinType.LEFT);
        Join<Object, Object> sizeStocks = root.join("sizeStocks", JoinType.LEFT);
        if (query != null) {
            query.distinct(true);
        }
        return cb.or(
                cb.greaterThan(root.get("stock"), 0),
                cb.greaterThan(variants.get("stock"), 0),
                cb.greaterThan(sizeStocks.get("stock"), 0)
        );
    }

    private ProductEntity toEntity(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.getId());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setPriceAmount(product.getPrice().amount());
        entity.setPriceCurrency(product.getPrice().currency());
        entity.setListPriceAmount(product.getListPrice() != null ? product.getListPrice().amount() : null);
        entity.setListPriceCurrency(product.getListPrice() != null ? product.getListPrice().currency() : null);
        entity.setImageUrl(product.getImageUrl());
        entity.setCondition(product.getCondition());
        entity.setBrand(product.getBrand().value());
        entity.setStock(product.getStock());
        entity.setActive(product.isActive());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        entity.setShippingOriginZone(product.getShippingOriginZone());

        List<ProductSizeStockEmbeddable> sizeEmbeddables = product.getSizeStocks().stream()
                .map(s -> new ProductSizeStockEmbeddable(s.getSize().name(), s.getStock()))
                .collect(Collectors.toList());
        entity.setSizeStocks(sizeEmbeddables);

        List<ProductVariantEmbeddable> variantEmbeddables = product.getVariants().stream()
                .map(v -> new ProductVariantEmbeddable(v.getColor(), v.getSize().name(), v.getStock()))
                .collect(Collectors.toList());
        entity.setVariants(variantEmbeddables);

        Set<CategoryEntity> cats = new HashSet<>(
                categoryJpaRepository.findAllById(product.getCategoryIds())
        );
        entity.setCategories(cats);

        return entity;
    }

    private Product toDomain(ProductEntity entity) {
        Product product = Product.create(
                entity.getName(),
                entity.getDescription(),
                new Money(entity.getPriceAmount(), entity.getPriceCurrency()),
                entity.getImageUrl(),
                entity.getCondition(),
                entity.getBrand(),
                entity.getStock(),
                entity.getListPriceAmount() != null
                        ? new Money(entity.getListPriceAmount(),
                        entity.getListPriceCurrency() == null || entity.getListPriceCurrency().isBlank()
                                ? entity.getPriceCurrency()
                                : entity.getListPriceCurrency())
                        : null
        );
        product.setId(entity.getId());
        product.setActive(entity.isActive());
        product.setCreatedAt(entity.getCreatedAt());
        product.setUpdatedAt(entity.getUpdatedAt());
        product.setAvgRating(entity.getAvgRating());
        product.setReviewCount(entity.getReviewCount());
        if (entity.getShippingOriginZone() != null) {
            product.setShippingOriginZone(entity.getShippingOriginZone());
        }
        List<ProductSizeStock> sizeStocks = entity.getSizeStocks().stream()
                .map(s -> new ProductSizeStock(ProductSize.valueOf(s.getSize()), s.getStock()))
                .collect(Collectors.toList());
        product.setSizeStocks(sizeStocks);

        List<ProductVariant> variants = (entity.getVariants() == null ? List.<ProductVariantEmbeddable>of() : entity.getVariants()).stream()
                .map(v -> new ProductVariant(v.getColor(), ProductSize.valueOf(v.getSize()), v.getStock()))
                .collect(Collectors.toList());
        product.setVariants(variants);

        // Map categories from entity
        if (entity.getCategories() != null && !entity.getCategories().isEmpty()) {
            Set<UUID> ids = entity.getCategories().stream()
                    .map(CategoryEntity::getId)
                    .collect(Collectors.toSet());
            List<String> slugs = entity.getCategories().stream()
                    .map(CategoryEntity::getSlug)
                    .sorted()
                    .collect(Collectors.toList());
            product.setCategoryIds(ids);
            product.setCategorySlugs(slugs);
        }

        return product;
    }
}
