package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.domain.ports.ProductRepository.ProductFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ListProductsUseCase {

    private final ProductRepository productRepository;

    public ListProductsUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // One parameter per filter the catalog listing actually exposes.
    @SuppressWarnings("java:S107")
    @Transactional(readOnly = true)
    public Page<ProductDto> execute(String condition, String brand,
                                     BigDecimal minPrice, BigDecimal maxPrice,
                                     Boolean active, Boolean inStock, String categorySlug,
                                     LocalDate createdFrom, LocalDate createdTo,
                                     Pageable pageable) {
        ProductFilter filter = new ProductFilter(
                condition,
                brand,
                minPrice,
                maxPrice,
                active,
                inStock,
                categorySlug,
                createdFrom,
                createdTo
        );
        return productRepository.findAll(filter, pageable).map(ProductMapper::toDto);
    }
}
