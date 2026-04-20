package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.events.StockUpdated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    public InventoryService(ProductRepository productRepository,
                             DomainEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void reserve(UUID productId, int qty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
    }

    @Transactional
    public void release(UUID productId, int qty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.releaseStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
    }

    /**
     * No-op for v1: stock was already reserved (decremented) on order creation.
     * In a future saga pattern this would commit the reservation.
     */
    public void confirm(UUID productId, int qty) {
        // intentional no-op
    }
}
