package com.pilarestilo.product.application.usecases;

import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.CategoryVariantFieldValidator;
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
    private final CategoryVariantFieldValidator variantFieldValidator;

    public CreateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher,
                                 CategoryVariantFieldValidator variantFieldValidator) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.variantFieldValidator = variantFieldValidator;
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
        return execute(name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds, null);
    }

    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds,
                               List<ProductVariantInput> variants) {
        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        Money listPrice = null;
        if (listPriceAmount != null) {
            String resolvedListCurrency = listPriceCurrency == null || listPriceCurrency.isBlank()
                    ? price.currency()
                    : listPriceCurrency;
            listPrice = Money.of(listPriceAmount, resolvedListCurrency);
        }
        ProductCondition productCondition = ProductCondition.valueOf(condition);

        Product product = Product.create(name, description, price, imageUrl, productCondition, brand, stock, listPrice);
        if (active != null) {
            product.setActive(active);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            product.setCategoryIds(categoryIds);
        }
        if (variants != null) {
            CategoryVariantFieldConfig config = variantFieldValidator.resolveConfig(product.getCategoryIds());
            variantFieldValidator.validate(config, variants);
            product.setVariants(variants.stream().map(this::toVariant).toList());
        }
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductCreated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }

    private ProductVariant toVariant(ProductVariantInput input) {
        try {
            return new ProductVariant(input.color(), input.size(), input.stock());
        } catch (DomainException _) {
            throw new DomainException("Invalid product variant size: " + input.size());
        }
    }
}
