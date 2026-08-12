package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting a discount used to remove the row, and {@code discount_code_usages.discount_id}
 * cascades — so it took the redemption ledger with it, including the PENDING rows of orders
 * still in flight. Those orders would then settle or release against nothing, having already
 * consumed a usage slot.
 */
@ExtendWith(MockitoExtension.class)
class DeleteDiscountUseCaseTest {

    @Mock DiscountRepository discountRepository;
    @InjectMocks DeleteDiscountUseCase useCase;

    private final UUID discountId = UUID.randomUUID();

    private Discount activeDiscount() {
        Discount d = Discount.create("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                Money.zero(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), 5);
        d.setId(discountId);
        return d;
    }

    @Test
    void deactivatesInsteadOfRemovingTheRow() {
        when(discountRepository.findById(discountId)).thenReturn(Optional.of(activeDiscount()));

        useCase.execute(discountId);

        ArgumentCaptor<Discount> saved = ArgumentCaptor.forClass(Discount.class);
        verify(discountRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
    }

    /** The point of the change: an inactive code is refused, so the caller's intent is met. */
    @Test
    void theRetiredCodeStopsValidating() {
        Discount discount = activeDiscount();
        when(discountRepository.findById(discountId)).thenReturn(Optional.of(discount));

        useCase.execute(discountId);

        assertThrows(com.pilarestilo.shared.domain.DomainException.class,
                () -> discount.validate(Money.of(BigDecimal.valueOf(50_000))));
    }

    @Test
    void retiringTwiceIsANoOp() {
        Discount discount = activeDiscount();
        discount.deactivate();
        when(discountRepository.findById(discountId)).thenReturn(Optional.of(discount));

        useCase.execute(discountId);

        verify(discountRepository, never()).save(discount);
    }

    @Test
    void throwsForAnUnknownDiscount() {
        when(discountRepository.findById(discountId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> useCase.execute(discountId));
        verify(discountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
