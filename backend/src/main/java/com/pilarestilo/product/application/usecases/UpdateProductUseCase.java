package com.pilarestilo.product.application.usecases;

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

    public UpdateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds) {
        return execute(
                id, name, description, priceAmount, priceCurrency,
                listPriceAmount, listPriceCurrency,
                imageUrl, condition, brand, stock, active, categoryIds, null
        );
    }

    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds,
                               List<ProductVariantInput> variants) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        Money listPrice = listPriceAmount == null
                ? null
                : Money.of(listPriceAmount, listPriceCurrency == null || listPriceCurrency.isBlank()
                ? price.currency()
                : listPriceCurrency);
        ProductCondition productCondition = ProductCondition.valueOf(condition);

        product.update(name, description, price, imageUrl, productCondition, brand, stock, active, listPrice);
        if (categoryIds != null) {
            product.setCategoryIds(categoryIds);
        }
        if (variants != null) {
            product.setVariants(variants.stream().map(this::toVariant).toList());
        }
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductUpdated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }

    private ProductVariant toVariant(ProductVariantInput input) {
        try {
            return new ProductVariant(input.color(), input.size(), input.stock());
        } catch (DomainException ex) {
            throw new DomainException("Invalid product variant size: " + input.size());
        }
    }
}
