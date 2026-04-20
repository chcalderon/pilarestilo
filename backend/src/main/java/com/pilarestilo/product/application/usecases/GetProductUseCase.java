package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.ports.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetProductUseCase {

    private final ProductRepository productRepository;

    public GetProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductDto execute(UUID id) {
        return productRepository.findById(id)
                .map(ProductMapper::toDto)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
    }
}
