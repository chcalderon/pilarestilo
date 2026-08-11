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
