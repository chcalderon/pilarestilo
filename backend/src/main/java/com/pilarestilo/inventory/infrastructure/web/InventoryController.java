package com.pilarestilo.inventory.infrastructure.web;

import com.pilarestilo.inventory.application.dto.InventoryProductDto;
import com.pilarestilo.product.application.usecases.GetProductUseCase;
import com.pilarestilo.product.application.usecases.ListProductsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ListProductsUseCase listProductsUseCase;
    private final GetProductUseCase getProductUseCase;

    public InventoryController(ListProductsUseCase listProductsUseCase, GetProductUseCase getProductUseCase) {
        this.listProductsUseCase = listProductsUseCase;
        this.getProductUseCase = getProductUseCase;
    }

    @GetMapping("/products")
    public Page<InventoryProductDto> list(
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "2") int lowStockThreshold,
            Pageable pageable
    ) {
        return listProductsUseCase.execute(condition, brand, null, null, active, null, category, pageable)
                .map(product -> InventoryMapper.toDto(product, lowStockThreshold));
    }

    @GetMapping("/products/{id}")
    public InventoryProductDto getById(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "2") int lowStockThreshold
    ) {
        return InventoryMapper.toDto(getProductUseCase.execute(id), lowStockThreshold);
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }
}
