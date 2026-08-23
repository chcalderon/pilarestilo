package com.pilarestilo.product.application.usecases;

import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductCreated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateProductUseCase {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    // Delegates via 'this' to the fuller overload below, bypassing its own @Transactional proxy --
    // harmless, since this overload's own @Transactional is already active by the time it does.
    // One parameter per field the product form actually submits.
    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds) {
        return execute(
                name, description, priceAmount, priceCurrency,
                listPriceAmount, listPriceCurrency,
                imageUrl, condition, brand, stock, active, categoryIds, null
        );
    }

    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds,
                               List<ProductVariantInput> variants) {
        return execute(name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds,
                variants, null);
    }

    /**
     * @param variantType which attribute pair the variants use, or null to leave it derived from
     *                    the categories — see Product.variantType.
     */
    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds,
                               List<ProductVariantInput> variants,
                               String variantType) {
        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        Money listPrice = listPriceAmount == null
                ? null
                : Money.of(listPriceAmount, listPriceCurrency == null || listPriceCurrency.isBlank()
                ? price.currency()
                : listPriceCurrency);
        ProductCondition productCondition = ProductCondition.valueOf(condition);

        Product product = Product.create(name, description, price, imageUrl, productCondition, brand, stock, listPrice);
        if (active != null) {
            product.setActive(active);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            product.setCategoryIds(categoryIds);
        }
        if (variants != null) {
            product.setVariants(variants.stream().map(this::toVariant).toList());
        }
        product.setVariantType(parseVariantType(variantType));
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductCreated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }

    /** Blank and null both mean "not stated", which is the storefront's cue to derive it. */
    private CategoryType parseVariantType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return CategoryType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unknown variant type: " + raw);
        }
    }

    private ProductVariant toVariant(ProductVariantInput input) {
        try {
            return new ProductVariant(input.color(), input.size(), input.stock());
        } catch (DomainException _) {
            throw new DomainException("Invalid product variant size: " + input.size());
        }
    }
}
