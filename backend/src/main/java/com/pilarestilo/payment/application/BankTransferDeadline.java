package com.pilarestilo.payment.application;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * When a pending bank transfer becomes eligible for automatic cancellation.
 *
 * <p>The value is a floor, not an exact moment. {@code AutoCancelPendingBankTransferUseCase} sweeps
 * on a cron — every 15 minutes by default — and processes at most 100 payments per run, so the
 * real cancellation lands somewhere in
 * {@code [createdAt + timeout, createdAt + timeout + cronPeriod + …]}.
 *
 * <p>The customer is told the earliest possible instant, because it is the only bound that never
 * over-promises. Copy must phrase it as "may be cancelled from", never "will be cancelled at":
 * understating the window costs nothing, overstating it creates a support ticket from someone
 * whose order was still alive.
 */
public final class BankTransferDeadline {

    private BankTransferDeadline() {}

    /**
     * @return empty when the payment is not a transfer, or when auto-cancel is switched off — in
     *         which case there is no deadline at all and the message must omit it rather than
     *         invent one.
     */
    public static Optional<Instant> forPayment(Payment payment, SystemSettings settings) {
        if (payment == null || settings == null) {
            return Optional.empty();
        }
        if (payment.getMethod() != PaymentMethod.TRANSFER) {
            return Optional.empty();
        }
        if (!settings.isBankTransferAutoCancelEnabled()) {
            return Optional.empty();
        }
        if (payment.getCreatedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(payment.getCreatedAt()
                .plus(settings.getBankTransferAutoCancelTimeoutMinutes(), ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MINUTES));
    }
}
