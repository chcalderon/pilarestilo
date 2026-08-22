package com.pilarestilo.productservice.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.pilarestilo.productservice.application.ProductQueryService;
import com.pilarestilo.productservice.web.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
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
    private static final String CREATED_AT = "createdAt";
    private static final String ID = "id";
    private static final Sort DEFAULT_ORDER = Sort.by(Sort.Direction.DESC, CREATED_AT).and(Sort.by(ID));

    /** Honours whatever the caller asked for, and supplies an order when they asked for none. */
    private static Pageable ordered(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_ORDER);
    }


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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            Pageable pageable
    ) {
        return queryService.list(condition, brand, minPrice, maxPrice, active, inStock, category, createdFrom, createdTo, ordered(pageable))
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
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            Pageable pageable
    ) {
        return queryService.search(q, active, inStock, condition, category, createdFrom, createdTo, ordered(pageable)).map(ProductMapper::toDto);
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }
}
