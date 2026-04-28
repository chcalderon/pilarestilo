package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.ListCashRegistersUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/caja")
public class AdminCajaController {

    private final ListCashRegistersUseCase listUseCase;

    public AdminCajaController(ListCashRegistersUseCase listUseCase) {
        this.listUseCase = listUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<CashRegisterDto> list(Pageable pageable) {
        return listUseCase.execute(pageable);
    }
}
