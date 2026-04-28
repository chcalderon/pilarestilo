package com.pilarestilo.cashregister.application.dto;

import com.pilarestilo.cashregister.domain.model.CashRegister;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CashRegisterDto(
        UUID id, UUID sellerId, String status,
        LocalDateTime openedAt, LocalDateTime closedAt,
        BigDecimal openingBalance, BigDecimal closingBalance,
        BigDecimal expectedBalance, BigDecimal difference, String notes,
        List<CashMovementDto> movements
) {
    public static CashRegisterDto from(CashRegister cr) {
        return new CashRegisterDto(
                cr.getId(), cr.getSellerId(), cr.getStatus().name(),
                cr.getOpenedAt(), cr.getClosedAt(),
                cr.getOpeningBalance(), cr.getClosingBalance(),
                cr.getExpectedBalance(), cr.getDifference(), cr.getNotes(),
                cr.getMovements().stream().map(CashMovementDto::from).toList());
    }
}
