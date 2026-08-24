package com.pilarestilo.discount.application;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRedemptionRepository;
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
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These guards previously existed only behind the "apply code" endpoint. Order creation
 * re-implemented a thinner version that skipped ownership and prior use, so they are tested here,
 * at the one place both callers now share.
 */
@ExtendWith(MockitoExtension.class)
class DiscountRedemptionServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final Money SUBTOTAL = Money.of(BigDecimal.valueOf(10_000));

    @Mock DiscountRepository discountRepository;
    @Mock DiscountRedemptionRepository redemptionRepository;
    @InjectMocks DiscountRedemptionService service;

    // Discount.validate() checks "today" against America/Santiago; anchoring the fixture to the
    // system default zone drifted a calendar day from that near UTC midnight.
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

    private Discount validDiscount() {
        Discount d = Discount.create("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                Money.zero(), LocalDate.now(STORE_ZONE).minusDays(1), LocalDate.now(STORE_ZONE).plusDays(30), 5);
        d.setId(UUID.randomUUID());
        return d;
    }

    @Test
    void evaluate_returnsAmount_forAValidCode() {
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(validDiscount()));

        var evaluation = service.evaluate("save10", SUBTOTAL, USER);

        assertThat(evaluation.amount().amount()).isEqualByComparingTo("1000");
        assertThat(evaluation.code()).isEqualTo("SAVE10");
    }

    @Test
    void evaluate_normalisesCase_andWhitespace() {
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(validDiscount()));

        assertThat(service.evaluate("  save10  ", SUBTOTAL, USER)).isNotNull();
    }

    @Test
    void evaluate_rejectsUnknownCode() {
        when(discountRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> service.evaluate("NOPE", SUBTOTAL, USER));
    }

    @Test
    void evaluate_rejectsBlankCode_withoutHittingTheRepository() {
        assertThrows(DomainException.class, () -> service.evaluate("   ", SUBTOTAL, USER));
        verify(discountRepository, never()).findByCode(any());
    }

    /** The gap that let a customer redeem a code assigned to somebody else. */
    @Test
    void evaluate_rejectsCodeAssignedToAnotherUser() {
        Discount d = validDiscount();
        d.setAssignedUserId(UUID.randomUUID());
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(d));

        assertThrows(DomainException.class, () -> service.evaluate("SAVE10", SUBTOTAL, USER));
    }

    @Test
    void evaluate_acceptsCodeAssignedToThisUser() {
        Discount d = validDiscount();
        d.setAssignedUserId(USER);
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(d));

        assertThat(service.evaluate("SAVE10", SUBTOTAL, USER)).isNotNull();
    }

    @Test
    void evaluate_rejectsWhenTheUserAlreadyHoldsAnActiveRedemption() {
        Discount d = validDiscount();
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(d));
        when(redemptionRepository.hasActiveRedemption(d.getId(), USER)).thenReturn(true);

        assertThrows(DomainException.class, () -> service.evaluate("SAVE10", SUBTOTAL, USER));
    }

    @Test
    void evaluate_rejectsExhaustedCode() {
        Discount d = validDiscount();
        d.setTimesUsed(5); // maxUses
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(d));

        assertThrows(DomainException.class, () -> service.evaluate("SAVE10", SUBTOTAL, USER));
    }

    @Test
    void evaluate_doesNotConsumeAnything() {
        Discount d = validDiscount();
        when(discountRepository.findByCode("SAVE10")).thenReturn(Optional.of(d));

        service.evaluate("SAVE10", SUBTOTAL, USER);

        assertThat(d.getTimesUsed()).isZero();
        verify(redemptionRepository, never()).reserve(any(), any(), any());
    }

    @Test
    void settleAndRelease_delegateToTheLedger() {
        when(redemptionRepository.settle(ORDER)).thenReturn(true);
        when(redemptionRepository.release(ORDER)).thenReturn(false);

        assertThat(service.settle(ORDER)).isTrue();
        assertThat(service.release(ORDER)).isFalse();
    }
}
