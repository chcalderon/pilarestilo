package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import com.pilarestilo.notificationservice.domain.view.PaymentView;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * When a pending bank transfer becomes eligible for automatic cancellation. Ported from the
 * monolith's {@code payment.application.BankTransferDeadline}.
 *
 * <p>The value is a floor, not an exact moment — the sweep is a cron. The customer is told the
 * earliest possible instant, and the copy phrases it as "may be cancelled from", never "will be
 * cancelled at".
 */
public final class BankTransferDeadline {

    private BankTransferDeadline() {}

    /**
     * @return empty when the payment is not a transfer, or when auto-cancel is off — in which case
     *         there is no deadline at all and the message must omit it rather than invent one.
     */
    public static Optional<Instant> forPayment(PaymentView payment, MessagingSettings settings) {
        if (payment == null || settings == null) {
            return Optional.empty();
        }
        if (!payment.isTransfer()) {
            return Optional.empty();
        }
        if (!settings.bankTransferAutoCancelEnabled()) {
            return Optional.empty();
        }
        if (payment.createdAt() == null) {
            return Optional.empty();
        }
        return Optional.of(payment.createdAt()
                .plus(settings.bankTransferAutoCancelTimeoutMinutes(), ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MINUTES));
    }
}
