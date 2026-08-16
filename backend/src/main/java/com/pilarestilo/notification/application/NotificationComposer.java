package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.payment.domain.model.Payment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    public NotificationMessage transferInstructions(Order order, Payment payment, Instant deadline) {
        String reference = order.getPublicReference();
        String amount = formatAmount(order.getTotalAmount().amount().toPlainString(),
                order.getTotalAmount().currency());

        StringBuilder body = new StringBuilder()
                .append("Tu pedido ").append(reference).append(" está reservado.\n\n")
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
        data.put("orderId", order.getId());
        data.put("orderReference", reference);
        data.put("totalAmount", order.getTotalAmount().amount());
        data.put("currency", order.getTotalAmount().currency());
        data.put("bankHolder", payment.getTransferAccountHolderName());
        data.put("bankName", payment.getTransferBankName());
        data.put("bankAccountType", payment.getTransferAccountType());
        data.put("bankAccountNumber", payment.getTransferAccountNumber());
        data.put("bankContactEmail", payment.getTransferAccountEmail());
        data.put("deadlineAt", deadline);
        data.put("deadlineLocal", deadline != null ? formatDeadline(deadline) : null);

        return new NotificationMessage(
                NotificationMessage.TRANSFER_INSTRUCTIONS,
                "Pedido " + reference + " — datos para tu transferencia",
                body.toString(),
                null,
                data,
                order.getId());
    }

    public NotificationMessage orderCancelled(Order order, String reason) {
        String reference = order.getPublicReference();
        String body = "Tu pedido " + reference + " fue cancelado.\n\n"
                + Optional.ofNullable(reason).filter(r -> !r.isBlank())
                        .map(r -> "Motivo: " + r + "\n\n")
                        .orElse("")
                + "Los productos volvieron a estar disponibles. Si aún quieres comprarlos, "
                + "puedes hacer un nuevo pedido.\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderReference", reference);
        data.put("reason", reason);

        return new NotificationMessage(
                NotificationMessage.ORDER_CANCELLED,
                "Pedido " + reference + " cancelado",
                body,
                null,
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

    public NotificationMessage orderConfirmation(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_CONFIRMATION,
                "Pedido " + reference + " confirmado",
                "Tu pedido " + reference + " fue creado correctamente.\n"
                        + "Te notificaremos por este canal cuando avance.\n",
                null,
                Map.of("orderId", orderId, "reference", reference),
                orderId);
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
                "Pedido " + reference + " en preparación",
                "Tu pedido " + reference + " está en preparación.\n"
                        + "Te avisaremos cuando sea despachado.\n",
                null,
                Map.of("orderId", orderId, "reference", reference),
                orderId);
    }

    public NotificationMessage orderShipped(Order order) {
        UUID orderId = order.getId();
        String reference = order.getPublicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_SHIPPED,
                "Pedido " + reference + " enviado",
                "Tu pedido " + reference + " ya fue enviado.\n"
                        + "Pronto llegará a destino.\n",
                null,
                Map.of("orderId", orderId, "reference", reference),
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
                "Pedido " + reference + " entregado",
                "Tu pedido " + reference + " quedó como entregado.\n"
                        + "Si aún no lo recibiste, respóndenos y lo revisamos.\n\n"
                        + "Nos ayudarías mucho contándonos qué te pareció.\n",
                null,
                Map.of("orderId", orderId, "reference", reference),
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
        data.put("orderId", order.getId());
        data.put("orderReference", reference);
        data.put("paymentId", payment.getId());
        data.put("amount", order.getTotalAmount().amount().toPlainString());
        data.put("currency", order.getTotalAmount().currency());
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

    private String formatAmount(String amount, String currency) {
        return currency + " " + amount;
    }
}
