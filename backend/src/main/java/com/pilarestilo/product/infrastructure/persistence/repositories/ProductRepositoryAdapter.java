package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductSizeStock;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductEntity;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductSizeStockEmbeddable;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductVariantEmbeddable;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import com.pilarestilo.varianttemplate.infrastructure.persistence.repositories.VariantTemplateJpaRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private static final String CATEGORIES_ATTR = "categories";

    private final ProductJpaRepository jpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final VariantTemplateJpaRepository variantTemplateJpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository,
                                    CategoryJpaRepository categoryJpaRepository,
                                    VariantTemplateJpaRepository variantTemplateJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.variantTemplateJpaRepository = variantTemplateJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.getId() == null
                ? new ProductEntity()
                : jpaRepository.findById(product.getId()).orElseGet(ProductEntity::new);
        applyToEntity(product, entity);
        ProductEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        // Read-only tx: toDomain() walks lazy associations (variant template field config,
        // categories), so the session must stay open through the mapping — otherwise a
        // non-transactional caller (e.g. PublishProductsBatchUseCase) hits a
        // LazyInitializationException on any product that has a variant template.
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Product> findAll(ProductFilter filter, Pageable pageable) {
        Specification<ProductEntity> spec = buildSpecification(filter);
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    @Override
    public void updateRatingSummary(UUID productId, BigDecimal avgRating, int reviewCount) {
        jpaRepository.updateRatingSummary(productId, avgRating, reviewCount);
    }

    @Override
    public int syncProductStockFromVariants(UUID productId) {
        return jpaRepository.syncProductStockFromVariants(productId);
    }

    @Override
    public int atomicReserveVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReserveVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicReleaseVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReleaseVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicConfirmVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicConfirmVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicReturnVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReturnVariantStock(productId, color, size, qty);
    }

    @Override
    public Page<Product> search(String term,
                                Boolean active,
                                Boolean inStock,
                                String condition,
                                String categorySlug,
                                LocalDate createdFrom,
                                LocalDate createdTo,
                                Pageable pageable) {
        String trimmedTerm = term == null ? "" : term.trim();
        String pattern = trimmedTerm.isEmpty() ? null : "%" + trimmedTerm.toLowerCase() + "%";
        Specification<ProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendSearchPredicates(predicates, root, query, cb, new SearchCriteria(pattern, active, inStock, condition));
            if (categorySlug != null && !categorySlug.isBlank()) {
                Join<Object, Object> filterCats = root.join(CATEGORIES_ATTR, JoinType.INNER);
                predicates.add(cb.equal(filterCats.get("slug"), categorySlug));
            }
            appendCreatedAtPredicates(predicates, root, cb, createdFrom, createdTo);
            if (query != null) query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    private record SearchCriteria(String pattern, Boolean active, Boolean inStock, String condition) {}

    private void appendSearchPredicates(List<Predicate> predicates,
                                        jakarta.persistence.criteria.Root<ProductEntity> root,
                                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                                        jakarta.persistence.criteria.CriteriaBuilder cb,
                                        SearchCriteria criteria) {
        if (criteria.pattern() != null) {
            predicates.add(buildSearchTermPredicate(root, cb, criteria.pattern()));
        }
        if (criteria.active() != null) {
            predicates.add(cb.equal(root.get("active"), criteria.active()));
        }
        if (Boolean.TRUE.equals(criteria.inStock())) {
            predicates.add(buildInStockPredicate(root, query, cb));
        }
        if (criteria.condition() != null && !criteria.condition().isBlank()) {
            predicates.add(cb.equal(root.get("condition"), ProductCondition.valueOf(criteria.condition())));
        }
    }

    private Predicate buildSearchTermPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                               jakarta.persistence.criteria.CriteriaBuilder cb,
                                               String pattern) {
        Join<Object, Object> textCats = root.join(CATEGORIES_ATTR, JoinType.LEFT);
        return cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("brand")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(textCats.get("nameEs")), pattern),
                cb.like(cb.lower(textCats.get("nameEn")), pattern),
                cb.like(cb.lower(textCats.get("slug")), pattern)
        );
    }

    private Specification<ProductEntity> buildSpecification(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendPriceAndAttributePredicates(predicates, root, cb, filter);
            if (Boolean.TRUE.equals(filter.inStock())) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (filter.categorySlug() != null) {
                Join<Object, Object> cats = root.join(CATEGORIES_ATTR, jakarta.persistence.criteria.JoinType.INNER);
                predicates.add(cb.equal(cats.get("slug"), filter.categorySlug()));
                if (query != null) query.distinct(true);
            }
            appendCreatedAtPredicates(predicates, root, cb, filter.createdFrom(), filter.createdTo());

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void appendPriceAndAttributePredicates(List<Predicate> predicates,
                                                    jakarta.persistence.criteria.Root<ProductEntity> root,
                                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                                    ProductFilter filter) {
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
    }

    private Predicate buildInStockPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                            jakarta.persistence.criteria.CriteriaQuery<?> query,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        /*
         * The sizeStocks join is gone: it repeated what the variants already say, and this is an
         * OR, so it could only ever widen the result. One fewer left join per product query.
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

    private void appendCreatedAtPredicates(List<Predicate> predicates,
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

    private void applyToEntity(Product product, ProductEntity entity) {
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

        // Hibernate's merge path clears and repopulates @ElementCollection lists in place
        // (CollectionType.replaceElements), so these must stay mutable -- .toList() here throws
        // UnsupportedOperationException on the next save of an already-persisted product.
        @SuppressWarnings("java:S6204")
        List<ProductSizeStockEmbeddable> sizeEmbeddables = product.getSizeStocks().stream()
                .map(s -> new ProductSizeStockEmbeddable(s.getSize(), s.getStock()))
                .collect(Collectors.toList());
        entity.setSizeStocks(sizeEmbeddables);

        @SuppressWarnings("java:S6204")
        List<ProductVariantEmbeddable> variantEmbeddables = product.getVariants().stream()
                .map(v -> new ProductVariantEmbeddable(v.getColor(), v.getSize(), v.getStockOnHand(), v.getStockReserved()))
                .collect(Collectors.toList());
        entity.setVariants(variantEmbeddables);

        // Mutable, same reason as the collections above: Hibernate repopulates it in place on merge.
        @SuppressWarnings("java:S6204")
        List<String> galleryImages = new ArrayList<>(product.getGalleryImageUrls());
        entity.setGalleryImageUrls(galleryImages);

        Set<CategoryEntity> cats = new HashSet<>(
                categoryJpaRepository.findAllById(product.getCategoryIds())
        );
        entity.setCategories(cats);

        entity.setVariantTemplate(product.getVariantTemplateId() != null
                ? variantTemplateJpaRepository.findById(product.getVariantTemplateId()).orElse(null)
                : null);
    }

    private Product toDomain(ProductEntity entity) {
        Money listPrice = null;
        if (entity.getListPriceAmount() != null) {
            String resolvedListCurrency = entity.getListPriceCurrency() == null || entity.getListPriceCurrency().isBlank()
                    ? entity.getPriceCurrency()
                    : entity.getListPriceCurrency();
            listPrice = new Money(entity.getListPriceAmount(), resolvedListCurrency);
        }
        Product product = Product.create(
                entity.getName(),
                entity.getDescription(),
                new Money(entity.getPriceAmount(), entity.getPriceCurrency()),
                entity.getImageUrl(),
                entity.getCondition(),
                entity.getBrand(),
                entity.getStock(),
                listPrice
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
        List<ProductVariant> variants = (entity.getVariants() == null ? List.<ProductVariantEmbeddable>of() : entity.getVariants()).stream()
                .map(v -> new ProductVariant(v.getColor(), v.getSize(), v.getStockOnHand(), v.getStockReserved()))
                .toList();
        /*
         * setVariants runs syncStocksFromVariants, which derives sizeStocks and stock from these
         * rows — so reading product_size_stocks first was work that got overwritten a line later.
         * The stored value is only kept for a product with no variants at all, where the
         * derivation returns early and there is nothing else to fall back on.
         */
        product.setVariants(variants);
        if (variants.isEmpty()) {
            product.setSizeStocks(entity.getSizeStocks().stream()
                    .map(sizeStock -> new ProductSizeStock(sizeStock.getSize(), sizeStock.getStock()))
                    .toList());
        }

        product.setGalleryImageUrls(
                entity.getGalleryImageUrls() == null ? List.of() : entity.getGalleryImageUrls());

        // Map categories from entity
        if (entity.getCategories() != null && !entity.getCategories().isEmpty()) {
            Set<UUID> ids = entity.getCategories().stream()
                    .map(CategoryEntity::getId)
                    .collect(Collectors.toSet());
            List<String> slugs = entity.getCategories().stream()
                    .map(CategoryEntity::getSlug)
                    .sorted()
                    .toList();
            List<String> categoryTypes = entity.getCategories().stream()
                    .map(category -> category.getCategoryType() != null ? category.getCategoryType().name() : "GENERIC")
                    .distinct()
                    .sorted()
                    .toList();
            product.setCategoryIds(ids);
            product.setCategorySlugs(slugs);
            product.setCategoryTypes(categoryTypes);
        }

        VariantTemplateEntity template = entity.getVariantTemplate();
        product.setVariantTemplateId(template != null ? template.getId() : null);
        product.setVariantFieldConfig(template != null
                ? fromRawConfig(template.getFieldConfig())
                : VariantFieldConfig.genericFallback());

        return product;
    }

    private static final String OPTIONS_KEY = "options";

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig fromRawConfig(Map<String, Object> raw) {
        return new VariantFieldConfig(
                fromRawField((Map<String, Object>) raw.get("primary")),
                fromRawField((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
        List<String> options = raw.get(OPTIONS_KEY) == null
                ? List.of()
                : ((List<Object>) raw.get(OPTIONS_KEY)).stream().map(String::valueOf).toList();
        return new VariantFieldConfig.FieldConfig(
                (String) raw.get("label"),
                VariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
