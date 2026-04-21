package com.pilarestilo.product.infrastructure.web.controllers;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.usecases.*;
import com.pilarestilo.product.infrastructure.web.requests.CreateProductRequest;
import com.pilarestilo.product.infrastructure.web.requests.UpdateProductRequest;
import com.pilarestilo.product.application.usecases.SearchProductsUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;

    public ProductController(CreateProductUseCase createProductUseCase,
                              UpdateProductUseCase updateProductUseCase,
                              GetProductUseCase getProductUseCase,
                              ListProductsUseCase listProductsUseCase,
                              DeleteProductUseCase deleteProductUseCase,
                              SearchProductsUseCase searchProductsUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.getProductUseCase = getProductUseCase;
        this.listProductsUseCase = listProductsUseCase;
        this.deleteProductUseCase = deleteProductUseCase;
        this.searchProductsUseCase = searchProductsUseCase;
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest request) {
        ProductDto dto = createProductUseCase.execute(
                request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
                request.listPriceAmount(), request.listPriceCurrency(),
                request.imageUrl(), request.condition(), request.brand(), request.stock(),
                request.active(), request.categoryIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public Page<ProductDto> list(
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        return listProductsUseCase.execute(condition, brand, minPrice, maxPrice, active, category, pageable);
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable UUID id) {
        return getProductUseCase.execute(id);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable UUID id,
                              @Valid @RequestBody UpdateProductRequest request) {
        return updateProductUseCase.execute(
                id, request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
                request.listPriceAmount(), request.listPriceCurrency(),
                request.imageUrl(), request.condition(), request.brand(),
                request.stock(), request.active(), request.categoryIds()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteProductUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public Page<ProductDto> search(
            @RequestParam(required = false, defaultValue = "") String q,
            Pageable pageable) {
        return searchProductsUseCase.execute(q, pageable);
    }
}
