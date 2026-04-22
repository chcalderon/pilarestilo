package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.application.InventoryCommandService;
import com.pilarestilo.inventoryservice.application.InventoryQueryService;
import com.pilarestilo.inventoryservice.web.dto.InventoryCommandRequest;
import com.pilarestilo.inventoryservice.web.dto.InventoryProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryQueryService queryService;
    private final InventoryCommandService commandService;

    public InventoryController(InventoryQueryService queryService,
                               InventoryCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/products")
    public Page<InventoryProductDto> list(
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "2") int lowStockThreshold,
            Pageable pageable
    ) {
        return queryService.list(condition, brand, active, category, q, pageable)
                .map(entity -> InventoryMapper.toDto(entity, lowStockThreshold));
    }

    @GetMapping("/products/{id}")
    public InventoryProductDto getById(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "2") int lowStockThreshold
    ) {
        try {
            return InventoryMapper.toDto(queryService.getById(id), lowStockThreshold);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }

    @PostMapping("/commands/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@RequestBody InventoryCommandRequest request) {
        executeCommand(() -> commandService.reserve(request.productId(), request.qty()));
    }

    @PostMapping("/commands/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@RequestBody InventoryCommandRequest request) {
        executeCommand(() -> commandService.release(request.productId(), request.qty()));
    }

    @PostMapping("/commands/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody InventoryCommandRequest request) {
        executeCommand(() -> commandService.confirm(request.productId(), request.qty()));
    }

    private void executeCommand(Runnable command) {
        try {
            command.run();
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
