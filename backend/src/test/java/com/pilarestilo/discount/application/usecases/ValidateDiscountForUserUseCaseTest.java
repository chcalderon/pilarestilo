package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The redemption rules themselves are covered by DiscountRedemptionServiceTest. What matters here
 * is that this endpoint delegates to that service rather than carrying its own copy — the two
 * drifting apart is what left order creation without the ownership and prior-use checks.
 */
@ExtendWith(MockitoExtension.class)
class ValidateDiscountForUserUseCaseTest {

    @Mock DiscountRedemptionService redemptionService;
    @InjectMocks ValidateDiscountForUserUseCase useCase;

    @Test
    void returnsTheDiscount_whenTheServiceAcceptsIt() {
        UUID user = UUID.randomUUID();
        Discount d = Discount.create("CODE", DiscountType.FIXED, BigDecimal.TEN, Money.zero(),
                LocalDate.now(), LocalDate.now().plusDays(10), 5);
        d.setId(UUID.randomUUID());

        when(redemptionService.evaluate(eq("CODE"), any(Money.class), eq(user)))
                .thenReturn(new DiscountRedemptionService.DiscountEvaluation(d, Money.of(BigDecimal.TEN)));

        assertThat(useCase.execute("CODE", BigDecimal.valueOf(1000), user).code()).isEqualTo("CODE");
    }

    @Test
    void propagatesTheServiceRejection() {
        UUID other = UUID.randomUUID();
        when(redemptionService.evaluate(eq("CODE"), any(Money.class), eq(other)))
                .thenThrow(new DomainException("Este código no está disponible para tu cuenta"));
        BigDecimal subtotal = BigDecimal.valueOf(1000);

        assertThrows(DomainException.class,
                () -> useCase.execute("CODE", subtotal, other));
    }
}
