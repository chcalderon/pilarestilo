package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.ports.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchProductsUseCase {

    private final ProductRepository productRepository;

    public SearchProductsUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> execute(String term, Boolean active, Boolean inStock, Pageable pageable) {
        String sanitized = term == null ? "" : term.trim();
        if (sanitized.isEmpty()) {
            return productRepository.findAll(
                            new ProductRepository.ProductFilter(null, null, null, null, active, inStock, null),
                            pageable)
                    .map(ProductMapper::toDto);
        }
        return productRepository.search(sanitized, active, inStock, pageable).map(ProductMapper::toDto);
    }
}
