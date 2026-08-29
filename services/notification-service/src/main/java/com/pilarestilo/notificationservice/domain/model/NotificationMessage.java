package com.pilarestilo.notificationservice.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A composed, channel-agnostic message.
 *
 * @param templateKey what this message is, e.g. {@code TRANSFER_INSTRUCTIONS}. Adapters may branch
 *                    on it for subject lines; they must never fail on an unknown value.
 * @param subject     used by email channels, ignored by WhatsApp.
 * @param bodyText    plain text. The only field every channel can render, so it must stand alone.
 * @param bodyHtml    optional richer version for email. Null means "send text only".
 * @param data        the structured facts behind the copy. n8n forwards this verbatim.
 * @param referenceId the order or payment this concerns, for correlation.
 */
public record NotificationMessage(
        String templateKey,
        String subject,
        String bodyText,
        String bodyHtml,
        Map<String, Object> data,
        UUID referenceId) {

    public static final String TRANSFER_INSTRUCTIONS = "TRANSFER_INSTRUCTIONS";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_CONFIRMATION = "ORDER_CONFIRMATION";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String ORDER_PREPARING = "ORDER_PREPARING";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String DISCOUNT_CODE_ASSIGNED = "DISCOUNT_CODE_ASSIGNED";
    /** Sent to whoever can approve payments, not to the customer. */
    public static final String PAYMENT_PROOF_SUBMITTED = "PAYMENT_PROOF_SUBMITTED";
    /** The boleta was registered: folio, net, VAT and total, so the buyer can quote it. */
    public static final String SALES_DOCUMENT_ISSUED = "SALES_DOCUMENT_ISSUED";
    /** Her return is on record, with the forty-five day clock the law starts. */
    public static final String RETURN_REQUESTED = "RETURN_REQUESTED";
    /** The return was accepted; the garment can travel back, at the shop's cost. */
    public static final String RETURN_APPROVED = "RETURN_APPROVED";
    /** The money went back, with the reference she needs to find it on her statement. */
    public static final String REFUND_REGISTERED = "REFUND_REGISTERED";
    /** Sent to whoever manages returns, not to the customer: a clock just started. */
    public static final String RETURN_REQUESTED_STAFF = "RETURN_REQUESTED_STAFF";
    /** The first message a new account gets, right after registration. */
    public static final String WELCOME = "WELCOME";

    public NotificationMessage {
        // Not Map.copyOf: it rejects null values, and absent facts are legitimately null here --
        // deadlineAt when auto-cancel is off, reason on a cancellation with none.
        data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
