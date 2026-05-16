package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AddCashMovementUseCase {
    private final CashRegisterRepository cashRegisterRepository;

    public AddCashMovementUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashMovementDto execute(UUID sellerId, CashMovementType type,
                                    CashMovementCategory category,
                                    BigDecimal amount, String description) {
        if (category == CashMovementCategory.INITIAL_BALANCE) {
            throw new DomainException("INITIAL_BALANCE movements are created automatically when opening a cash register");
        }
        if (category == CashMovementCategory.CASH_SALE) {
            throw new DomainException("CASH_SALE movements are created automatically from POS orders");
        }
        CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
                .orElseThrow(() -> new DomainException("No open cash register"));
        int sizeBefore = cr.getMovements().size();
        cr.addMovement(type, category, amount, description, null, sellerId);
        CashRegister saved = cashRegisterRepository.save(cr);
        return CashMovementDto.from(saved.getMovements().get(sizeBefore));
    }
}
