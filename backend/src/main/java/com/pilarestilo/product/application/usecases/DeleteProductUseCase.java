package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.domain.ports.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeleteProductUseCase {

    private final ProductRepository productRepository;

    public DeleteProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void execute(UUID id) {
        productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
        productRepository.deleteById(id);
    }
}
