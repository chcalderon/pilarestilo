package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateDiscountForUserUseCaseTest {

    @Mock DiscountRepository discountRepository;
    @InjectMocks ValidateDiscountForUserUseCase useCase;

    @Test
    void throwsWhenCodeAssignedToOtherUser() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Discount d = Discount.create("CODE", DiscountType.FIXED,
            BigDecimal.TEN, Money.zero(),
            LocalDate.now(), LocalDate.now().plusDays(10), 5);
        d.setAssignedUserId(owner);

        when(discountRepository.findByCode("CODE")).thenReturn(Optional.of(d));

        assertThrows(DomainException.class, () ->
            useCase.execute("CODE", BigDecimal.valueOf(1000), other));
    }
}
