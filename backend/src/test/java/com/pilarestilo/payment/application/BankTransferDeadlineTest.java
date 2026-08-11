package com.pilarestilo.payment.application;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BankTransferDeadlineTest {

    private static final Instant CREATED = Instant.parse("2026-08-10T14:00:00Z");

    @Mock Payment payment;
    @Mock SystemSettings settings;

    private void stubPayment(PaymentMethod method) {
        lenient().when(payment.getMethod()).thenReturn(method);
        lenient().when(payment.getCreatedAt()).thenReturn(CREATED);
    }

    private void stubSettings(boolean enabled, int timeoutMinutes) {
        lenient().when(settings.isBankTransferAutoCancelEnabled()).thenReturn(enabled);
        lenient().when(settings.getBankTransferAutoCancelTimeoutMinutes()).thenReturn(timeoutMinutes);
    }

    @Test
    void isCreatedAtPlusTheConfiguredTimeout() {
        stubPayment(PaymentMethod.TRANSFER);
        stubSettings(true, 30);

        assertThat(BankTransferDeadline.forPayment(payment, settings))
                .contains(CREATED.plus(30, ChronoUnit.MINUTES));
    }

    /**
     * No deadline exists when the sweep is switched off, and the composer must then omit the
     * paragraph entirely rather than print a time that will never arrive.
     */
    @Test
    void isEmptyWhenAutoCancelIsDisabled() {
        stubPayment(PaymentMethod.TRANSFER);
        stubSettings(false, 30);

        assertThat(BankTransferDeadline.forPayment(payment, settings)).isEmpty();
    }

    @Test
    void isEmptyForNonTransferPayments() {
        stubPayment(PaymentMethod.WEBPAY);
        stubSettings(true, 30);

        assertThat(BankTransferDeadline.forPayment(payment, settings)).isEmpty();
    }

    @Test
    void isEmptyWhenTheresNothingToComputeFrom() {
        assertThat(BankTransferDeadline.forPayment(null, settings)).isEmpty();
        assertThat(BankTransferDeadline.forPayment(payment, null)).isEmpty();
    }

    /** Seconds would be false precision: the sweep runs on a cron, not to the second. */
    @Test
    void isTruncatedToTheMinute() {
        lenient().when(payment.getMethod()).thenReturn(PaymentMethod.TRANSFER);
        lenient().when(payment.getCreatedAt()).thenReturn(Instant.parse("2026-08-10T14:00:37.482Z"));
        stubSettings(true, 30);

        assertThat(BankTransferDeadline.forPayment(payment, settings))
                .contains(Instant.parse("2026-08-10T14:30:00Z"));
    }

    @Test
    void honoursTheMinimumAndMaximumTimeouts() {
        stubPayment(PaymentMethod.TRANSFER);

        stubSettings(true, 5);
        assertThat(BankTransferDeadline.forPayment(payment, settings))
                .contains(CREATED.plus(5, ChronoUnit.MINUTES));

        stubSettings(true, 1440);
        assertThat(BankTransferDeadline.forPayment(payment, settings))
                .contains(CREATED.plus(1440, ChronoUnit.MINUTES));
    }

    @Test
    void unusedIdIsIrrelevantToTheCalculation() {
        // Guards against a future refactor keying the deadline off the payment id instead of its
        // creation time.
        stubPayment(PaymentMethod.TRANSFER);
        stubSettings(true, 30);
        lenient().when(payment.getId()).thenReturn(UUID.randomUUID());

        assertThat(BankTransferDeadline.forPayment(payment, settings))
                .contains(CREATED.plus(30, ChronoUnit.MINUTES));
    }
}
