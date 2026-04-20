package com.pilarestilo.discount.infrastructure.web.controllers;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.usecases.*;
import com.pilarestilo.discount.infrastructure.web.requests.CreateDiscountRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

    private final CreateDiscountUseCase createDiscountUseCase;
    private final GetDiscountUseCase getDiscountUseCase;
    private final ListDiscountsUseCase listDiscountsUseCase;
    private final ValidateDiscountUseCase validateDiscountUseCase;
    private final DeleteDiscountUseCase deleteDiscountUseCase;

    public DiscountController(CreateDiscountUseCase createDiscountUseCase,
                               GetDiscountUseCase getDiscountUseCase,
                               ListDiscountsUseCase listDiscountsUseCase,
                               ValidateDiscountUseCase validateDiscountUseCase,
                               DeleteDiscountUseCase deleteDiscountUseCase) {
        this.createDiscountUseCase = createDiscountUseCase;
        this.getDiscountUseCase = getDiscountUseCase;
        this.listDiscountsUseCase = listDiscountsUseCase;
        this.validateDiscountUseCase = validateDiscountUseCase;
        this.deleteDiscountUseCase = deleteDiscountUseCase;
    }

    @PostMapping
    public ResponseEntity<DiscountDto> create(@Valid @RequestBody CreateDiscountRequest request) {
        DiscountDto dto = createDiscountUseCase.execute(
                request.code(), request.type(), request.value(),
                request.minOrderAmount(), request.validFrom(), request.validUntil(), request.maxUses()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public DiscountDto getById(@PathVariable UUID id) {
        return getDiscountUseCase.execute(id);
    }

    @GetMapping
    public Page<DiscountDto> list(Pageable pageable) {
        return listDiscountsUseCase.execute(pageable);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteDiscountUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validate")
    public BigDecimal validate(@RequestParam String code, @RequestParam BigDecimal subtotal) {
        return validateDiscountUseCase.execute(code, subtotal).amount();
    }
}
