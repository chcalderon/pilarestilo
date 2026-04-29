package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.ListCashRegistersUseCase;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/caja")
public class AdminCajaController {

    private final ListCashRegistersUseCase listUseCase;

    public AdminCajaController(ListCashRegistersUseCase listUseCase) {
        this.listUseCase = listUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<CashRegisterDto> list(
            @RequestParam(required = false) CashRegisterStatus status,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return listUseCase.execute(pageable, status, sellerId, from, to);
    }
}
