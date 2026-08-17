package com.pilarestilo.returns.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * How long the customer has to change her mind.
 *
 * <p>Ley 19.496 art. 3 bis: ten days from receiving the product. Ninety if the shop never sent
 * written confirmation of the conditions of the offer — which is why the order confirmation email
 * now goes out for every payment method including transfer, where it used to be skipped.
 *
 * <p>The shop sends that confirmation on every order, so ten days is the window that applies. The
 * ninety-day figure is kept here as the constant it is rather than as a magic number in a comment:
 * if a message ever fails to go out, this is where the consequence is written down.
 */
public final class RetractoWindow {

    public static final Duration WITH_WRITTEN_CONFIRMATION = Duration.ofDays(10);
    public static final Duration WITHOUT_WRITTEN_CONFIRMATION = Duration.ofDays(90);

    private RetractoWindow() {}

    /**
     * @param deliveredAt when the customer received it; the clock starts there, not at purchase
     * @return true while the customer may still retract
     */
    public static boolean isOpen(Instant deliveredAt, Instant now) {
        if (deliveredAt == null) {
            // Nothing received means nothing to retract from. A sale not yet delivered is cancelled,
            // not retracted, and that path already exists.
            return false;
        }
        return now.isBefore(deliveredAt.plus(WITH_WRITTEN_CONFIRMATION));
    }

    public static Instant closesAt(Instant deliveredAt) {
        return deliveredAt == null ? null : deliveredAt.plus(WITH_WRITTEN_CONFIRMATION);
    }
}
