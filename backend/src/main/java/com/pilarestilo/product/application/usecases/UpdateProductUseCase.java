package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.VariantTemplateValidator;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductUpdated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateProductUseCase {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;
    private final VariantTemplateValidator variantTemplateValidator;

    public UpdateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher,
                                 VariantTemplateValidator variantTemplateValidator) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.variantTemplateValidator = variantTemplateValidator;
    }

    // Delegates via 'this' to the fuller overload below, bypassing its own @Transactional proxy --
    // harmless, since this overload's own @Transactional is already active by the time it does.
    // One parameter per field the product form actually submits.
    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds, UUID variantTemplateId) {
        return execute(id, name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds, variantTemplateId,
                null, List.of());
    }

    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds, UUID variantTemplateId,
                               List<ProductVariantInput> variants, List<String> galleryImageUrls) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

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

        product.update(name, description, price, imageUrl, productCondition, brand, stock, active, listPrice);
        product.setGalleryImageUrls(galleryImageUrls);
        if (categoryIds != null) {
            product.setCategoryIds(categoryIds);
        }
        product.setVariantTemplateId(variantTemplateId);
        if (variants != null) {
            VariantFieldConfig config = variantTemplateValidator.resolveConfig(variantTemplateId);
            variantTemplateValidator.validate(config, variants);
            product.setVariants(variants.stream().map(this::toVariant).toList());
        }
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductUpdated(saved.getId(), saved.getName()));

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
