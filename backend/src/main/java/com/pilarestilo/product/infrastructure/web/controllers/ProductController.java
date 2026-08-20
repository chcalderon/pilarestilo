package com.pilarestilo.product.infrastructure.web.controllers;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.usecases.*;
import com.pilarestilo.product.infrastructure.web.requests.CreateProductRequest;
import com.pilarestilo.product.infrastructure.web.requests.ProductVariantRequest;
import com.pilarestilo.product.infrastructure.web.requests.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    /*
     * A page with no order is not a page. Neither implementation stated one, so Postgres returned
     * rows in whatever order suited it and the two disagreed about the same fifteen products —
     * which means paging the catalogue could show a garment twice or never. Newest first is what a
     * boutique wants, and the id breaks ties so the sequence is total.
     */
    private static final Sort DEFAULT_ORDER = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

    /** Honours whatever the caller asked for, and supplies an order when they asked for none. */
    private static Pageable ordered(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_ORDER);
    }


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
                request.active(), request.categoryIds(), toVariantInputs(request.variants()),
                request.variantType()
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
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            Pageable pageable) {
        return listProductsUseCase.execute(
                condition,
                brand,
                minPrice,
                maxPrice,
                active,
                inStock,
                category,
                createdFrom,
                createdTo,
                ordered(pageable)
        );
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
                request.stock(), request.active(), request.categoryIds(), toVariantInputs(request.variants()),
                request.variantType()
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
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            Pageable pageable) {
        return searchProductsUseCase.execute(q, active, inStock, condition, category, createdFrom, createdTo, ordered(pageable));
    }

    private static List<ProductVariantInput> toVariantInputs(List<ProductVariantRequest> requests) {
        if (requests == null) {
            return null;
        }
        return requests.stream()
                .map(v -> new ProductVariantInput(v.color(), v.size(), v.stock()))
                .toList();
    }
}
