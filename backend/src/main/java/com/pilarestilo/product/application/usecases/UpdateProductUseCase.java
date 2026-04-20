package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductUpdated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        ProductCondition productCondition = ProductCondition.valueOf(condition);

        product.update(name, description, price, imageUrl, productCondition, brand, stock, active);
        if (categoryIds != null) {
            product.setCategoryIds(categoryIds);
        }
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductUpdated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }
}
