package com.pilarestilo.notification.application;

import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.user.domain.events.UserRegistered;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every customer-facing string lives here.
 *
 * <p>Adapters render what this produces; they no longer write copy. Adding a field to the transfer
 * email is now one edit instead of six, and n8n picks it up for free because {@code data} is a map.
 */
@Service
public class NotificationComposer {

    /** Deadlines are quoted in local time; a UTC instant would be meaningless to the customer. */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // n8n forwards `data` verbatim, so these keys are the contract with every downstream workflow --
    // one place to change a key name is what keeps that contract from drifting between messages.
    private static final String KEY_ORDER_ID = "orderId";
    private static final String KEY_ORDER_REFERENCE = "orderReference";
    private static final String KEY_REFERENCE = "reference";
    private static final String KEY_TOTAL_AMOUNT = "totalAmount";
    private static final String KEY_CURRENCY = "currency";
    private static final String KEY_DEADLINE_AT = "deadlineAt";
    private static final String KEY_REASON = "reason";
    private static final String KEY_RETURN_ID = "returnId";

    private static final String PREFIX_TU_PEDIDO = "Tu pedido ";
    private static final String PREFIX_PEDIDO = "Pedido ";
    private static final String LABEL_NUMERO_PEDIDO = "Número de pedido";

    public NotificationMessage transferInstructions(Order order, Payment payment, Instant deadline) {
        String reference = order.getPublicReference();
        String amount = formatAmount(order.getTotalAmount().amount().toPlainString(),
                order.getTotalAmount().currency());

        StringBuilder body = new StringBuilder()
                .append(PREFIX_TU_PEDIDO).append(reference).append(" está reservado.\n\n")
                .append("Monto a transferir: ").append(amount).append("\n\n")
                .append("Datos para la transferencia:\n")
                .append("  Titular: ").append(payment.getTransferAccountHolderName()).append('\n')
                .append("  Banco: ").append(payment.getTransferBankName()).append('\n')
                .append("  Tipo de cuenta: ").append(payment.getTransferAccountType()).append('\n')
                .append("  N° de cuenta: ").append(payment.getTransferAccountNumber()).append('\n')
                .append("  Correo: ").append(payment.getTransferAccountEmail()).append("\n\n")
                .append("Escribe ").append(reference).append(" en el mensaje de la transferencia ")
                .append("para que podamos identificar tu pago.\n\n");

        // "Sube tu comprobante", never "transfiere". AutoCancelPendingBankTransferUseCase selects
        // status = PENDING; SubmitPaymentProofUseCase moves the payment to SUBMITTED and removes it
        // from the sweep permanently. Making the transfer changes nothing on its own -- uploading
        // the proof is what stops the clock, and saying otherwise is the most misleading thing this
        // message could do.
        if (deadline != null) {
            body.append("Sube tu comprobante antes de las ").append(formatDeadline(deadline))
                    .append(". A partir de esa hora tu pedido puede cancelarse automáticamente ")
                    .append("y el stock quedará liberado.\n");
        } else {
            // Auto-cancel disabled: there is no deadline, so printing one would be a lie.
            body.append("Sube tu comprobante desde Mi Cuenta cuando hayas hecho la transferencia.\n");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_TOTAL_AMOUNT, order.getTotalAmount().amount());
        data.put(KEY_CURRENCY, order.getTotalAmount().currency());
        data.put("bankHolder", payment.getTransferAccountHolderName());
        data.put("bankName", payment.getTransferBankName());
        data.put("bankAccountType", payment.getTransferAccountType());
        data.put("bankAccountNumber", payment.getTransferAccountNumber());
        data.put("bankContactEmail", payment.getTransferAccountEmail());
        data.put(KEY_DEADLINE_AT, deadline);
        data.put("deadlineLocal", deadline != null ? formatDeadline(deadline) : null);

        return new NotificationMessage(
                NotificationMessage.TRANSFER_INSTRUCTIONS,
                PREFIX_PEDIDO + reference + " — datos para tu transferencia",
                body.toString(),
                transferInstructionsHtml(payment, deadline, reference, amount),
                data,
                order.getId());
    }

    /**
     * The one message a customer works from rather than reads: they copy the account number, they
     * type the reference into the transfer, and they need the amount to the peso.
     *
     * <p>So the amount is the highlight, the bank details are a table of one fact per row instead of
     * a paragraph to pick apart, and the deadline is a note rather than another sentence in the
     * middle. The wording is the plain-text version's, unchanged — including "sube tu comprobante"
     * rather than "transfiere", which is the thing that actually stops the clock.
     */
    private String transferInstructionsHtml(Payment payment,
                                            Instant deadline,
                                            String reference,
                                            String amount) {
        EmailLayout.Builder email = EmailLayout.titled("Datos para tu transferencia")
                .paragraph(PREFIX_TU_PEDIDO + reference + " está reservado. Transfiere el monto exacto "
                        + "a la cuenta de abajo.")
                .highlight("Monto a transferir", amount)
                .details(List.of(
                        new String[]{"Titular", payment.getTransferAccountHolderName()},
                        new String[]{"Banco", payment.getTransferBankName()},
                        new String[]{"Tipo de cuenta", payment.getTransferAccountType()},
                        new String[]{"N° de cuenta", payment.getTransferAccountNumber()},
                        new String[]{"Correo", payment.getTransferAccountEmail()},
                        new String[]{"Mensaje", reference}))
                .paragraph("Escribe " + reference + " en el mensaje de la transferencia para que "
                        + "podamos identificar tu pago.");

        if (deadline != null) {
            email.note("Sube tu comprobante desde Mi Cuenta antes de las "
                    + formatDeadline(deadline) + ". Sin comprobante, el pedido puede cancelarse "
                    + "y el stock quedará liberado.");
        } else {
            email.paragraph("Sube tu comprobante desde Mi Cuenta cuando hayas hecho la transferencia.");
        }
        return email.build();
    }

    public NotificationMessage orderCancelled(Order order, String reason) {
        String reference = order.getPublicReference();
        String body = PREFIX_TU_PEDIDO + reference + " fue cancelado.\n\n"
                + Optional.ofNullable(reason).filter(r -> !r.isBlank())
                        .map(r -> "Motivo: " + r + "\n\n")
                        .orElse("")
                + "Los productos volvieron a estar disponibles. Si aún quieres comprarlos, "
                + "puedes hacer un nuevo pedido.\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_REASON, reason);

        return new NotificationMessage(
                NotificationMessage.ORDER_CANCELLED,
                PREFIX_PEDIDO + reference + " cancelado",
                body,
                orderCancelledHtml(reference, reason),
                data,
                order.getId());
    }

    /*
     * The five short messages below. Their wording used to be duplicated across eight adapters,
     * so each channel drifted on its own: SMTP carried real Spanish prose while the WhatsApp and
     * log senders emitted only a template key and an id. Collapsing them here gives every channel
     * the same copy, and a new field is one edit instead of eight.
     *
     * <p>They take an id rather than the aggregate because that is all their callers hold. Where
     * an order reference would read better than a UUID, the caller has to load the order first —
     * a change to what the customer sees, not a refactor, so it is not made here.
     */

    /**
     * The written confirmation of the conditions of the sale.
     *
     * <p>Not a courtesy note: the Ley 21.398 requires the supplier to send confirmation of the offer
     * in writing, including what was bought and for how much. Without it the customer's right of
     * withdrawal runs for ninety days instead of ten. So this message carries the lines, the amounts
     * and the shipping, and it is sent for every payment method — including TRANSFER, which used to
     * receive only bank details and therefore never learned what it had agreed to.
     */
    public NotificationMessage orderConfirmation(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        String total = formatAmount(order.getTotalAmount().amount().toPlainString(),
                order.getTotalAmount().currency());

        StringBuilder body = new StringBuilder()
                .append(PREFIX_TU_PEDIDO).append(reference).append(" fue creado correctamente.\n\n")
                .append("Detalle:\n");
        for (var item : order.getItems()) {
            body.append("  ").append(item.getProductName())
                    .append(variantSuffix(item.getVariantColor(), item.getVariantSize()))
                    .append(" x").append(item.getQuantity())
                    .append(" — ")
                    .append(formatAmount(item.getUnitPrice().amount().toPlainString(),
                            item.getUnitPrice().currency()))
                    .append('\n');
        }
        body.append('\n')
                .append("Subtotal: ").append(formatAmount(
                        order.getSubtotal().amount().toPlainString(), order.getSubtotal().currency())).append('\n');
        if (order.getDiscountAmount().amount().signum() > 0) {
            body.append("Descuento: -").append(formatAmount(
                    order.getDiscountAmount().amount().toPlainString(),
                    order.getDiscountAmount().currency())).append('\n');
        }
        body.append("Total: ").append(total).append("\n\n")
                .append("Envío: ").append(shippingLine(order)).append("\n\n")
                .append("Tienes 10 días desde que recibes el pedido para arrepentirte de la compra "
                        + "y pedir la devolución, según la Ley del Consumidor.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, orderId);
        data.put(KEY_REFERENCE, reference);
        data.put("subtotalAmount", order.getSubtotal().amount());
        data.put("discountAmount", order.getDiscountAmount().amount());
        data.put("netAmount", order.getNetAmount().amount());
        data.put("taxAmount", order.getTaxAmount().amount());
        data.put("taxRate", order.getTaxRate());
        data.put(KEY_TOTAL_AMOUNT, order.getTotalAmount().amount());
        data.put(KEY_CURRENCY, order.getTotalAmount().currency());
        data.put("shippingCourierName", order.getShippingCourierName());
        data.put("shippingZoneCode", order.getShippingZoneCode());

        return new NotificationMessage(
                NotificationMessage.ORDER_CONFIRMATION,
                PREFIX_PEDIDO + reference + " confirmado",
                body.toString(),
                orderConfirmationHtml(order, reference, total),
                data,
                orderId);
    }

    private String orderConfirmationHtml(Order order, String reference, String total) {
        List<String[]> lines = order.getItems().stream()
                .map(item -> new String[]{
                        item.getProductName() + variantSuffix(item.getVariantColor(), item.getVariantSize())
                                + " x" + item.getQuantity(),
                        formatAmount(item.getUnitPrice().amount().toPlainString(),
                                item.getUnitPrice().currency())})
                .toList();

        EmailLayout.Builder email = EmailLayout.titled("Recibimos tu pedido")
                .paragraph("Gracias por comprar en Pilar Estilo. Esto es lo que pediste; te "
                        + "avisaremos por aquí en cada paso.")
                .highlight(LABEL_NUMERO_PEDIDO, reference)
                .details(lines);

        List<String[]> amounts = new java.util.ArrayList<>();
        amounts.add(new String[]{"Subtotal", formatAmount(
                order.getSubtotal().amount().toPlainString(), order.getSubtotal().currency())});
        if (order.getDiscountAmount().amount().signum() > 0) {
            amounts.add(new String[]{"Descuento", "-" + formatAmount(
                    order.getDiscountAmount().amount().toPlainString(),
                    order.getDiscountAmount().currency())});
        }
        amounts.add(new String[]{"Total", total});
        amounts.add(new String[]{"Envío", shippingLine(order)});

        return email
                .details(amounts)
                .note("Tienes 10 días desde que recibes el pedido para arrepentirte de la compra y "
                        + "pedir la devolución, según la Ley del Consumidor.")
                .build();
    }

    private String shippingLine(Order order) {
        String courier = Optional.ofNullable(order.getShippingCourierName())
                .filter(name -> !name.isBlank())
                .orElse(order.getShippingCourierId());
        return Optional.ofNullable(courier).orElse("por confirmar")
                + " · " + Optional.ofNullable(order.getShippingZoneCode()).orElse("");
    }

    private String variantSuffix(String color, String size) {
        String variant = java.util.stream.Stream.of(color, size)
                .filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + " / " + b)
                .orElse(null);
        return variant == null ? "" : " (" + variant + ")";
    }

    public NotificationMessage paymentReceived(UUID paymentId) {
        return new NotificationMessage(
                NotificationMessage.PAYMENT_RECEIVED,
                "Pago " + shortId(paymentId) + " recibido",
                "Confirmamos el pago " + paymentId + ".\n"
                        + "Gracias por tu compra en Pilar Estilo.\n",
                null,
                Map.of("paymentId", paymentId),
                paymentId);
    }

    /**
     * A cancellation says what happened and what is still possible, in that order.
     *
     * <p>The reason is only printed when there is one: an empty "Motivo:" reads as the shop having
     * cancelled the order for no stated cause.
     */
    private String orderCancelledHtml(String reference, String reason) {
        EmailLayout.Builder email = EmailLayout.titled("Tu pedido fue cancelado")
                .highlight(LABEL_NUMERO_PEDIDO, reference);
        Optional.ofNullable(reason)
                .filter(r -> !r.isBlank())
                .ifPresent(r -> email.note("Motivo: " + r));
        return email
                .paragraph("Los productos volvieron a estar disponibles. Si aún quieres comprarlos, "
                        + "puedes hacer un nuevo pedido cuando quieras.")
                .build();
    }

    /*
     * These take the Order rather than its id so they can quote the public reference. They used to
     * print the raw UUID into the customer's inbox -- "Tu pedido 053ec893-5011-4cbe-a078-... esta
     * en preparación" -- which is nothing anybody can quote back, match against a bank statement or
     * read out on the phone. transferInstructions had it right all along.
     */
    public NotificationMessage orderPreparing(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_PREPARING,
                PREFIX_PEDIDO + reference + " en preparación",
                PREFIX_TU_PEDIDO + reference + " está en preparación.\n"
                        + "Te avisaremos cuando sea despachado.\n",
                EmailLayout.titled("Estamos preparando tu pedido")
                        .paragraph("Tu pago quedó confirmado y ya estamos armando el paquete.")
                        .highlight(LABEL_NUMERO_PEDIDO, reference)
                        .paragraph("Te escribimos de nuevo apenas salga.")
                        .build(),
                Map.of(KEY_ORDER_ID, orderId, KEY_REFERENCE, reference),
                orderId);
    }

    public NotificationMessage orderShipped(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_SHIPPED,
                PREFIX_PEDIDO + reference + " enviado",
                PREFIX_TU_PEDIDO + reference + " ya fue enviado.\n"
                        + "Pronto llegará a destino.\n",
                EmailLayout.titled("Tu pedido va en camino")
                        .paragraph("Ya salió de nuestras manos y está en viaje.")
                        .highlight(LABEL_NUMERO_PEDIDO, reference)
                        .paragraph("Cuando llegue, avísanos desde tu cuenta para cerrar el pedido.")
                        .build(),
                Map.of(KEY_ORDER_ID, orderId, KEY_REFERENCE, reference),
                orderId);
    }

    /**
     * Closes the loop out loud.
     *
     * <p>Nothing was sent when an order reached DELIVERED, which matters most in the case the
     * customer did not cause: a dispatch sitting fifteen days is confirmed on their behalf. Told
     * nothing, somebody whose parcel never arrived has no prompt to say so -- the order just closes.
     *
     * <p>One wording covers both routes deliberately. Nothing recorded distinguishes the job's
     * confirmation from the customer's own, and guessing from elapsed time would eventually thank
     * somebody for a click they never made. Offering the way out regardless costs a sentence and is
     * true either way, since people misclick too.
     */
    public NotificationMessage orderDelivered(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_DELIVERED,
                PREFIX_PEDIDO + reference + " entregado",
                PREFIX_TU_PEDIDO + reference + " quedó como entregado.\n"
                        + "Si aún no lo recibiste, respóndenos y lo revisamos.\n\n"
                        + "Nos ayudarías mucho contándonos qué te pareció.\n",
                EmailLayout.titled("Tu pedido quedó como entregado")
                        .highlight(LABEL_NUMERO_PEDIDO, reference)
                        .note("Si aún no lo recibiste, respóndenos este correo y lo revisamos.")
                        .paragraph("Nos ayudarías mucho contándonos qué te pareció.")
                        .build(),
                Map.of(KEY_ORDER_ID, orderId, KEY_REFERENCE, reference),
                orderId);
    }

    public NotificationMessage discountCodeAssigned(String code) {
        return new NotificationMessage(
                NotificationMessage.DISCOUNT_CODE_ASSIGNED,
                "Código de descuento exclusivo para ti",
                "Tienes un código de descuento exclusivo: " + code + "\n"
+ ""
                        + "Úsalo en tu próxima compra en Pilar Estilo.\n",
                null,
                Map.of("code", code),
                null);
    }

    /**
     * Tells a reviewer a receipt is waiting, with what they need to judge it without opening
     * anything: which order, how much, and who paid.
     *
     * <p>The only message in here addressed to staff rather than a customer, which is why it
     * names the buyer and carries the order id for the panel to link to.
     */
    public NotificationMessage paymentProofSubmitted(Order order, Payment payment, String buyerName) {
        String reference = order.getPublicReference();
        /* The amount lives on the order; a Payment carries the method and the proof, not a total. */
        String amount = formatAmount(
                order.getTotalAmount().amount().toPlainString(), order.getTotalAmount().currency());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put("paymentId", payment.getId());
        data.put("amount", order.getTotalAmount().amount().toPlainString());
        data.put(KEY_CURRENCY, order.getTotalAmount().currency());
        data.put("buyerName", buyerName);
        data.put("proofReference", payment.getProofReference());

        return new NotificationMessage(
                NotificationMessage.PAYMENT_PROOF_SUBMITTED,
                "Comprobante recibido — pedido " + reference,
                "Un cliente subió el comprobante de su transferencia.\n\n"
                        + "Pedido: " + reference + "\n"
                        + "Monto: " + amount + "\n"
                        + "Cliente: " + buyerName + "\n\n"
                        + "Revísalo en el panel, en Pagos → Pendientes de revisión.\n",
                null,
                data,
                order.getId());
    }

    /** The first message a new account gets, right after registration. */
    public NotificationMessage welcome(String fullName) {
        return welcome(fullName, null);
    }

    /**
     * Same message, with a coupon block when the shop is running one and this account qualified
     * for it. {@code coupon} is null whenever none was issued — feature off, or marketing consent
     * required and not given.
     */
    public NotificationMessage welcome(String fullName, UserRegistered.WelcomeDiscount coupon) {
        StringBuilder body = new StringBuilder("Hola ").append(fullName).append(", bienvenida a Pilar Estilo.\n\n")
                .append("Ya puedes explorar el catálogo y hacer tu primera compra.\n");

        EmailLayout.Builder email = EmailLayout.titled("Bienvenida a Pilar Estilo")
                .paragraph("Hola " + fullName + ", gracias por crear tu cuenta.")
                .paragraph("Ya puedes explorar el catálogo y hacer tu primera compra.");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", fullName);

        if (coupon != null) {
            String condition = couponCondition(coupon);
            body.append("\nTienes un código de bienvenida: ").append(coupon.code())
                    .append(" (").append(condition).append("), válido hasta ")
                    .append(coupon.validUntil()).append(".\n");
            email.highlight("Tu código de bienvenida", coupon.code())
                    .note(condition + ". Válido hasta " + coupon.validUntil() + ".");
            data.put("welcomeDiscountCode", coupon.code());
            data.put("welcomeDiscountValidUntil", coupon.validUntil());
        }

        return new NotificationMessage(
                NotificationMessage.WELCOME,
                "Bienvenida a Pilar Estilo",
                body.toString(),
                email.build(),
                data,
                null);
    }

    private String couponCondition(UserRegistered.WelcomeDiscount coupon) {
        String amount = "PERCENTAGE".equals(coupon.type())
                ? coupon.value().toPlainString() + "%"
                : formatAmount(coupon.value().toPlainString(), "CLP");
        return coupon.minOrderAmount() != null && coupon.minOrderAmount().signum() > 0
                ? amount + " de descuento en compras sobre " + formatAmount(coupon.minOrderAmount().toPlainString(), "CLP")
                : amount + " de descuento en tu próxima compra";
    }

    /** The first segment of a UUID: enough for a customer to quote, short enough for a subject. */
    private static String shortId(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8);
    }

    /** Same-day deadlines show only the time; anything later needs the date to be unambiguous. */
    private String formatDeadline(Instant deadline) {
        var local = deadline.atZone(STORE_ZONE);
        var now = Instant.now().atZone(STORE_ZONE);
        return local.toLocalDate().equals(now.toLocalDate())
                ? local.format(TIME)
                : local.format(DATE_TIME);
    }

    /**
     * Tells the buyer their boleta exists and what it says.
     *
     * <p>The file is not attached: it lives outside the public media root and is read through an
     * authenticated endpoint. What the customer needs to quote is the folio and the amounts, and
     * those are here.
     */
    public NotificationMessage salesDocumentIssued(Order order, SalesDocument document) {
        String reference = order.getPublicReference();
        String currency = document.getTotalAmount().currency();
        boolean isCreditNote = document.getType() == SalesDocumentType.NOTA_CREDITO;
        String documentName = switch (document.getType()) {
            case FACTURA -> "factura";
            case NOTA_CREDITO -> "nota de crédito";
            case BOLETA -> "boleta";
        };
        // A credit note is the document that undoes a purchase, so it cannot be announced with the
        // wording of one that confirms it.
        String opening = isCreditNote
                ? "Emitimos la nota de crédito que deja sin efecto tu pedido " + reference + "."
                : "Emitimos la " + documentName + " de tu pedido " + reference + ".";

        String body = opening + "\n\n"
                + "Folio: " + document.getFolio() + "\n"
                + "Neto: " + formatAmount(document.getNetAmount().amount().toPlainString(), currency) + "\n"
                + "IVA (" + document.getTaxRate().toPlainString() + "%): "
                + formatAmount(document.getTaxAmount().amount().toPlainString(), currency) + "\n"
                + "Total: " + formatAmount(document.getTotalAmount().amount().toPlainString(), currency) + "\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put("documentId", document.getId());
        data.put("documentType", document.getType().name());
        data.put("folio", document.getFolio());
        data.put("netAmount", document.getNetAmount().amount());
        data.put("taxAmount", document.getTaxAmount().amount());
        data.put("taxRate", document.getTaxRate());
        data.put(KEY_TOTAL_AMOUNT, document.getTotalAmount().amount());
        data.put(KEY_CURRENCY, currency);

        return new NotificationMessage(
                NotificationMessage.SALES_DOCUMENT_ISSUED,
                (isCreditNote ? "Nota de crédito " : "Boleta ") + document.getFolio()
                        + " de tu pedido " + reference,
                body,
                EmailLayout.titled(isCreditNote
                                ? "Tu nota de crédito está emitida"
                                : "Tu " + documentName + " está emitida")
                        .paragraph(opening)
                        .highlight("Folio", document.getFolio())
                        .details(List.of(
                                new String[]{"Neto", formatAmount(
                                        document.getNetAmount().amount().toPlainString(), currency)},
                                new String[]{"IVA (" + document.getTaxRate().toPlainString() + "%)",
                                        formatAmount(document.getTaxAmount().amount().toPlainString(), currency)},
                                new String[]{"Total", formatAmount(
                                        document.getTotalAmount().amount().toPlainString(), currency)}))
                        .build(),
                data,
                order.getId());
    }

    /**
     * The receipt of a return: what she asked for, and by when the law says the money is back.
     *
     * <p>The deadline is quoted rather than kept internal on purpose. It is the shop's obligation,
     * not a secret, and a date in writing is what keeps a return from drifting.
     */
    public NotificationMessage returnRequested(Order order, ReturnRequest request) {
        String reference = order.getPublicReference();
        String deadline = formatDate(request.getDeadlineAt());
        boolean isRetracto = request.getKind() == ReturnKind.RETRACTO;
        String opening = isRetracto
                ? "Recibimos tu arrepentimiento del pedido " + reference + "."
                : "Recibimos tu solicitud de devolución del pedido " + reference + ".";

        String body = opening + "\n\n"
                + "No necesitas justificarlo. Te escribiremos con los pasos para enviarnos la prenda; "
                + "el envío de vuelta lo pagamos nosotros.\n\n"
                + "Te devolvemos el dinero a más tardar el " + deadline + ".\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.getId());
        data.put("kind", request.getKind().name());
        data.put(KEY_REASON, request.getReason());
        data.put(KEY_DEADLINE_AT, request.getDeadlineAt());

        return new NotificationMessage(
                NotificationMessage.RETURN_REQUESTED,
                "Recibimos tu devolución del pedido " + reference,
                body,
                EmailLayout.titled(isRetracto
                                ? "Tu arrepentimiento está registrado"
                                : "Tu devolución está registrada")
                        .paragraph(opening + " No necesitas justificarlo.")
                        .highlight("Plazo para devolverte el dinero", deadline)
                        .paragraph("Te escribiremos con los pasos para enviarnos la prenda. "
                                + "El envío de vuelta lo pagamos nosotros.")
                        .build(),
                data,
                order.getId());
    }

    /** Approved: the garment travels back, and the shop pays that trip. */
    public NotificationMessage returnApproved(Order order, ReturnRequest request) {
        String reference = order.getPublicReference();
        String deadline = formatDate(request.getDeadlineAt());

        String body = "Aprobamos la devolución de tu pedido " + reference + ".\n\n"
                + "Coordinamos contigo el retiro o te enviamos la etiqueta de envío: el costo es "
                + "nuestro, no tuyo.\n\n"
                + "Apenas recibamos la prenda te devolvemos el dinero, y en todo caso antes del "
                + deadline + ".\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.getId());
        data.put(KEY_DEADLINE_AT, request.getDeadlineAt());

        return new NotificationMessage(
                NotificationMessage.RETURN_APPROVED,
                "Devolución aprobada — pedido " + reference,
                body,
                EmailLayout.titled("Devolución aprobada")
                        .paragraph("Aprobamos la devolución de tu pedido " + reference + ".")
                        .paragraph("Coordinamos el retiro o te enviamos la etiqueta de envío. "
                                + "El costo es nuestro, no tuyo.")
                        .highlight("Dinero de vuelta antes del", deadline)
                        .build(),
                data,
                order.getId());
    }

    /** The money moved. The garment may still be in the workshop; that is a separate track. */
    public NotificationMessage refundRegistered(Order order, ReturnRequest request) {
        String reference = order.getPublicReference();
        String amount = request.getRefundAmount() == null
                ? formatAmount(order.getTotalAmount().amount().toPlainString(),
                        order.getTotalAmount().currency())
                : formatAmount(request.getRefundAmount().amount().toPlainString(),
                        request.getRefundAmount().currency());
        String method = request.getRefundMethod() == null ? "—" : request.getRefundMethod().name();
        boolean hasReference = request.getRefundReference() != null
                && !request.getRefundReference().isBlank();

        StringBuilder body = new StringBuilder()
                .append("Te devolvimos el dinero de tu pedido ").append(reference).append(".\n\n")
                .append("Monto: ").append(amount).append('\n')
                .append("Medio: ").append(method).append('\n');
        if (hasReference) {
            body.append("Referencia: ").append(request.getRefundReference()).append('\n');
        }
        body.append("\nSegún tu banco puede tardar unos días en aparecer en tu cartola.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.getId());
        data.put("refundAmount", request.getRefundAmount() == null
                ? null : request.getRefundAmount().amount());
        data.put("refundMethod", request.getRefundMethod() == null
                ? null : request.getRefundMethod().name());
        data.put("refundReference", request.getRefundReference());
        data.put("refundedAt", request.getRefundedAt());

        List<String[]> rows = hasReference
                ? List.of(new String[]{"Monto", amount},
                          new String[]{"Medio", method},
                          new String[]{"Referencia", request.getRefundReference()})
                : List.of(new String[]{"Monto", amount},
                          new String[]{"Medio", method});

        return new NotificationMessage(
                NotificationMessage.REFUND_REGISTERED,
                "Te devolvimos el dinero — pedido " + reference,
                body.toString(),
                EmailLayout.titled("Reembolso realizado")
                        .paragraph("Te devolvimos el dinero de tu pedido " + reference + ".")
                        .details(rows)
                        .note("Según tu banco puede tardar unos días en aparecer en tu cartola.")
                        .build(),
                data,
                order.getId());
    }

    /**
     * Tells whoever manages returns that one just opened, and by when the money has to be back.
     *
     * <p>Addressed to staff rather than the customer, like the receipt alert: a return nobody looks
     * at is how a forty-five day obligation quietly expires.
     */
    public NotificationMessage returnRequestedForStaff(Order order, ReturnRequest request,
                                                       String buyerName) {
        String reference = order.getPublicReference();
        String deadline = formatDate(request.getDeadlineAt());
        boolean isRetracto = request.getKind() == ReturnKind.RETRACTO;

        String body = (isRetracto
                        ? "Una clienta se arrepintió de su pedido " + reference + "."
                        : "Se abrió una devolución del pedido " + reference + ".")
                + "\n\n"
                + "Cliente: " + buyerName + "\n"
                + "Motivo: " + request.getReason() + "\n"
                + "Plazo legal para devolver el dinero: " + deadline + "\n\n"
                + (isRetracto
                        ? "Un retracto dentro de plazo no se rechaza. Revísalo en el panel, en Devoluciones.\n"
                        : "Revísalo en el panel, en Devoluciones.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.getId());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.getId());
        data.put("kind", request.getKind().name());
        data.put(KEY_REASON, request.getReason());
        data.put("buyerName", buyerName);
        data.put(KEY_DEADLINE_AT, request.getDeadlineAt());

        return new NotificationMessage(
                NotificationMessage.RETURN_REQUESTED_STAFF,
                (isRetracto ? "Retracto" : "Devolución") + " — pedido " + reference,
                body,
                null,
                data,
                order.getId());
    }

    /** A date the customer can act on: local time, never an instant. */
    private String formatDate(Instant instant) {
        return instant == null ? "—" : DATE.format(instant.atZone(STORE_ZONE));
    }

    private String formatAmount(String amount, String currency) {
        return currency + " " + amount;
    }
}
