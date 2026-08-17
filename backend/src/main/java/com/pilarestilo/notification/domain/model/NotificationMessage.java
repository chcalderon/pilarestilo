package com.pilarestilo.notification.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A composed, channel-agnostic message.
 *
 * <p>Copy used to live inside each adapter, built with {@code String.format}, so adding one field
 * to the transfer email meant editing six files and the n8n payload separately. Composition now
 * happens once in {@code NotificationComposer} and adapters only render.
 *
 * @param templateKey what this message is, e.g. {@code TRANSFER_INSTRUCTIONS}. Adapters may branch
 *                    on it for subject lines or routing; they must never fail on an unknown value.
 * @param subject     used by email channels, ignored by WhatsApp.
 * @param bodyText    plain text. The only field every channel can render, so it must stand alone.
 * @param bodyHtml    optional richer version for email. Null means "send text only".
 * @param data        the structured facts behind the copy. n8n forwards this verbatim, which is
 *                    what lets downstream workflows change wording without a backend deploy — and
 *                    what makes a new field free for that channel.
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

    public NotificationMessage {
        // Not Map.copyOf: it rejects null values, and absent facts are legitimately null here --
        // deadlineAt when auto-cancel is off, reason on a cancellation with none. A defensive copy
        // that throws on the very case the copy exists to carry is worse than no copy.
        data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
