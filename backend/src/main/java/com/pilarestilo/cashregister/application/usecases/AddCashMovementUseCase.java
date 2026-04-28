package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
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
                                    BigDecimal amount, String description) {
        if (type == CashMovementType.SALE || type == CashMovementType.REFUND) {
            throw new DomainException("SALE and REFUND movements are created automatically");
        }
        CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
                .orElseThrow(() -> new DomainException("No open cash register"));
        int sizeBefore = cr.getMovements().size();
        cr.addMovement(type, amount, description, null, sellerId);
        CashRegister saved = cashRegisterRepository.save(cr);
        return CashMovementDto.from(saved.getMovements().get(sizeBefore));
    }
}
