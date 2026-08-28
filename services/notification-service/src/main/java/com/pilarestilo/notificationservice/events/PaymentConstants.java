package com.pilarestilo.notificationservice.events;

import java.util.UUID;

/** Constants shared with the monolith's payment domain. */
public final class PaymentConstants {

    private PaymentConstants() {}

    /**
     * {@code Payment.systemCancel} stamps this reviewer id so a listener can tell the auto-cancel
     * job apart from a human rejection. Copied verbatim from
     * {@code com.pilarestilo.payment.domain.model.Payment}.
     */
    public static final UUID SYSTEM_REVIEWER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
}
