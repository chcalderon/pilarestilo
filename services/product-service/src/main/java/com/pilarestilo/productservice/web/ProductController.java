package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.application.ProductQueryService;
import com.pilarestilo.productservice.web.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService queryService;

    public ProductController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Page<ProductDto> list(
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String category,
            Pageable pageable
    ) {
        return queryService.list(condition, brand, minPrice, maxPrice, active, inStock, category, pageable)
                .map(ProductMapper::toDto);
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable UUID id) {
        try {
            return ProductMapper.toDto(queryService.getById(id));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/search")
    public Page<ProductDto> search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean inStock,
            Pageable pageable
    ) {
        return queryService.search(q, active, inStock, pageable).map(ProductMapper::toDto);
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }
}
