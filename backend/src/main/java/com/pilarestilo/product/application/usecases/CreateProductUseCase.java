package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductCreated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds) {
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
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductCreated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }
}
