package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.PaymentView;
import com.pilarestilo.notificationservice.domain.view.ReturnView;
import com.pilarestilo.notificationservice.domain.view.SalesDocumentView;
import com.pilarestilo.notificationservice.domain.view.WelcomeDiscount;
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
 * Every customer-facing string lives here. Adapters render what this produces; they no longer write
 * copy. Ported from the monolith with the aggregate parameters swapped for read-view records — the
 * message-building bodies are unchanged.
 */
@Service
public class NotificationComposer {

    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    public NotificationMessage transferInstructions(OrderView order, PaymentView payment, Instant deadline) {
        String reference = order.publicReference();
        String amount = formatAmount(order.total().amount().toPlainString(), order.total().currency());

        StringBuilder body = new StringBuilder()
                .append(PREFIX_TU_PEDIDO).append(reference).append(" está reservado.\n\n")
                .append("Monto a transferir: ").append(amount).append("\n\n")
                .append("Datos para la transferencia:\n")
                .append("  Titular: ").append(payment.transferAccountHolderName()).append('\n')
                .append("  Banco: ").append(payment.transferBankName()).append('\n')
                .append("  Tipo de cuenta: ").append(payment.transferAccountType()).append('\n')
                .append("  N° de cuenta: ").append(payment.transferAccountNumber()).append('\n')
                .append("  Correo: ").append(payment.transferAccountEmail()).append("\n\n")
                .append("Escribe ").append(reference).append(" en el mensaje de la transferencia ")
                .append("para que podamos identificar tu pago.\n\n");

        if (deadline != null) {
            body.append("Sube tu comprobante antes de las ").append(formatDeadline(deadline))
                    .append(". A partir de esa hora tu pedido puede cancelarse automáticamente ")
                    .append("y el stock quedará liberado.\n");
        } else {
            body.append("Sube tu comprobante desde Mi Cuenta cuando hayas hecho la transferencia.\n");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_TOTAL_AMOUNT, order.total().amount());
        data.put(KEY_CURRENCY, order.total().currency());
        data.put("bankHolder", payment.transferAccountHolderName());
        data.put("bankName", payment.transferBankName());
        data.put("bankAccountType", payment.transferAccountType());
        data.put("bankAccountNumber", payment.transferAccountNumber());
        data.put("bankContactEmail", payment.transferAccountEmail());
        data.put(KEY_DEADLINE_AT, deadline);
        data.put("deadlineLocal", deadline != null ? formatDeadline(deadline) : null);

        return new NotificationMessage(
                NotificationMessage.TRANSFER_INSTRUCTIONS,
                PREFIX_PEDIDO + reference + " — datos para tu transferencia",
                body.toString(),
                transferInstructionsHtml(payment, deadline, reference, amount),
                data,
                order.id());
    }

    private String transferInstructionsHtml(PaymentView payment,
                                            Instant deadline,
                                            String reference,
                                            String amount) {
        EmailLayout.Builder email = EmailLayout.titled("Datos para tu transferencia")
                .paragraph(PREFIX_TU_PEDIDO + reference + " está reservado. Transfiere el monto exacto "
                        + "a la cuenta de abajo.")
                .highlight("Monto a transferir", amount)
                .details(List.of(
                        new String[]{"Titular", payment.transferAccountHolderName()},
                        new String[]{"Banco", payment.transferBankName()},
                        new String[]{"Tipo de cuenta", payment.transferAccountType()},
                        new String[]{"N° de cuenta", payment.transferAccountNumber()},
                        new String[]{"Correo", payment.transferAccountEmail()},
                        new String[]{"Mensaje", reference}))
                .paragraph("Escribe " + reference + " en el mensaje de la transferencia para que "
                        + "podamos identificar tu pago.");

        if (deadline != null) {
            email.note("Antes de la fecha límite", "Sube tu comprobante desde Mi cuenta antes de las "
                    + formatDeadline(deadline) + ". Sin comprobante, el pedido puede cancelarse "
                    + "y el stock quedará liberado.");
        } else {
            email.paragraph("Sube tu comprobante desde Mi Cuenta cuando hayas hecho la transferencia.");
        }
        return email.build();
    }

    public NotificationMessage orderCancelled(OrderView order, String reason) {
        String reference = order.publicReference();
        String body = PREFIX_TU_PEDIDO + reference + " fue cancelado.\n\n"
                + Optional.ofNullable(reason).filter(r -> !r.isBlank())
                        .map(r -> "Motivo: " + r + "\n\n")
                        .orElse("")
                + "Los productos volvieron a estar disponibles. Si aún quieres comprarlos, "
                + "puedes hacer un nuevo pedido.\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_REASON, reason);

        return new NotificationMessage(
                NotificationMessage.ORDER_CANCELLED,
                PREFIX_PEDIDO + reference + " cancelado",
                body,
                orderCancelledHtml(reference, reason),
                data,
                order.id());
    }

    /**
     * The written confirmation of the conditions of the sale — required by the Ley 21.398, sent for
     * every payment method including TRANSFER.
     */
    public NotificationMessage orderConfirmation(OrderView order) {
        UUID orderId = order.id();
        String reference = order.publicReference();
        String total = formatAmount(order.total().amount().toPlainString(), order.total().currency());

        StringBuilder body = new StringBuilder()
                .append(PREFIX_TU_PEDIDO).append(reference).append(" fue creado correctamente.\n\n")
                .append("Detalle:\n");
        for (var item : order.items()) {
            body.append("  ").append(item.productName())
                    .append(variantSuffix(item.variantColor(), item.variantSize()))
                    .append(" x").append(item.quantity())
                    .append(" — ")
                    .append(formatAmount(item.unitPrice().amount().toPlainString(),
                            item.unitPrice().currency()))
                    .append('\n');
        }
        body.append('\n')
                .append("Subtotal: ").append(formatAmount(
                        order.subtotal().amount().toPlainString(), order.subtotal().currency())).append('\n');
        if (order.discount().amount().signum() > 0) {
            body.append("Descuento: -").append(formatAmount(
                    order.discount().amount().toPlainString(),
                    order.discount().currency())).append('\n');
        }
        body.append("Total: ").append(total).append("\n\n")
                .append("Envío: ").append(shippingLine(order)).append("\n\n")
                .append("Tienes 10 días desde que recibes el pedido para arrepentirte de la compra "
                        + "y pedir la devolución, según la Ley del Consumidor.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, orderId);
        data.put(KEY_REFERENCE, reference);
        data.put("subtotalAmount", order.subtotal().amount());
        data.put("discountAmount", order.discount().amount());
        data.put("netAmount", order.net().amount());
        data.put("taxAmount", order.tax().amount());
        data.put("taxRate", order.taxRate());
        data.put(KEY_TOTAL_AMOUNT, order.total().amount());
        data.put(KEY_CURRENCY, order.total().currency());
        data.put("shippingCourierName", order.shippingCourierName());
        data.put("shippingZoneCode", order.shippingZoneCode());

        return new NotificationMessage(
                NotificationMessage.ORDER_CONFIRMATION,
                PREFIX_PEDIDO + reference + " confirmado",
                body.toString(),
                orderConfirmationHtml(order, reference, total),
                data,
                orderId);
    }

    private String orderConfirmationHtml(OrderView order, String reference, String total) {
        List<EmailLayout.Line> lines = order.items().stream()
                .map(item -> new EmailLayout.Line(
                        item.productName(),
                        lineVariantAndQty(item),
                        formatAmount(item.unitPrice().amount().toPlainString(),
                                item.unitPrice().currency())))
                .toList();

        List<String[]> totals = new java.util.ArrayList<>();
        totals.add(new String[]{"Subtotal", formatAmount(
                order.subtotal().amount().toPlainString(), order.subtotal().currency())});
        if (order.discount().amount().signum() > 0) {
            totals.add(new String[]{"Descuento", "-" + formatAmount(
                    order.discount().amount().toPlainString(), order.discount().currency())});
        }
        totals.add(new String[]{"Envío", shippingLine(order)});
        totals.add(new String[]{"Total", total});

        return EmailLayout.titled("Recibimos tu pedido")
                .eyebrow("Confirmación de pedido")
                .paragraph("Gracias por comprar en Pilar Estilo. Esto es lo que pediste; te "
                        + "escribimos por aquí en cada paso, desde la preparación hasta la entrega.")
                .orderSummary(reference, formatDate(Instant.now()), lines, totals)
                .route("Cómo ver el estado", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
                .note("Si cambias de opinión", "Tienes 10 días desde que recibes el pedido para "
                        + "pedir la devolución, sin dar motivo, según la Ley del Consumidor. La "
                        + "solicitas desde Mi cuenta › Pedidos.")
                .build();
    }

    /** "Crudo / M · x2", or just "x2" when the item has no real variant. */
    private String lineVariantAndQty(OrderView.OrderItemView item) {
        String variant = java.util.stream.Stream.of(item.variantColor(), item.variantSize())
                .filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + " / " + b)
                .orElse(null);
        return (variant == null ? "" : variant + " · ") + "x" + item.quantity();
    }

    private String shippingLine(OrderView order) {
        String courier = Optional.ofNullable(order.shippingCourierName())
                .filter(name -> !name.isBlank())
                .orElse(order.shippingCourierId());
        return Optional.ofNullable(courier).orElse("por confirmar")
                + " · " + Optional.ofNullable(order.shippingZoneCode()).orElse("");
    }

    private String variantSuffix(String color, String size) {
        String variant = java.util.stream.Stream.of(color, size)
                .filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + " / " + b)
                .orElse(null);
        return variant == null ? "" : " (" + variant + ")";
    }

    public NotificationMessage paymentReceived(OrderView order, PaymentView payment) {
        String reference = order.publicReference();
        String total = formatAmount(order.total().amount().toPlainString(), order.total().currency());
        int itemCount = order.items().stream().mapToInt(OrderView.OrderItemView::quantity).sum();
        String methodLabel = methodLabel(payment.method());

        String body = "Recibimos el pago de tu pedido " + reference + " " + methodLabel + ".\n\n"
                + "Ya estamos preparando el pedido; te avisamos por aquí apenas salga a despacho.\n\n"
                + "Puedes seguirlo en pilarestilo.com, en Mi cuenta > Pedidos.\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put("paymentId", payment.id());
        data.put("method", payment.method());
        data.put(KEY_TOTAL_AMOUNT, order.total().amount());
        data.put(KEY_CURRENCY, order.total().currency());

        return new NotificationMessage(
                NotificationMessage.PAYMENT_RECEIVED,
                "Pago confirmado — pedido " + reference,
                body,
                EmailLayout.titled("Estamos preparando tu pedido")
                        .eyebrow("Pago confirmado")
                        .paragraph("Recibimos tu pago. Ya estamos armando el paquete y te avisamos por "
                                + "aquí apenas salga a despacho.")
                        .orderSummary(reference, formatDate(payment.createdAt()), List.of(),
                                List.of(new String[]{"Productos", itemCount + " · " + total},
                                        new String[]{"Pago", capitalize(methodLabel)}))
                        .route("Cómo seguirlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
                        .build(),
                data,
                order.id());
    }

    private static String methodLabel(String method) {
        if (method == null) {
            return "con tarjeta o transferencia";
        }
        return switch (method) {
            case "TRANSFER" -> "por transferencia";
            case "MERCADO_PAGO" -> "con Mercado Pago";
            default -> "con tarjeta";
        };
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String orderCancelledHtml(String reference, String reason) {
        EmailLayout.Builder email = EmailLayout.titled("Tu pedido fue cancelado")
                .highlight(LABEL_NUMERO_PEDIDO, reference);
        Optional.ofNullable(reason)
                .filter(r -> !r.isBlank())
                .ifPresent(r -> email.note("Motivo", r));
        return email
                .paragraph("Los productos volvieron a estar disponibles. Si aún quieres comprarlos, "
                        + "puedes hacer un nuevo pedido cuando quieras.")
                .build();
    }

    public NotificationMessage orderShipped(OrderView order) {
        UUID orderId = order.id();
        String reference = order.publicReference();
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

    public NotificationMessage orderDelivered(OrderView order) {
        UUID orderId = order.id();
        String reference = order.publicReference();
        return new NotificationMessage(
                NotificationMessage.ORDER_DELIVERED,
                PREFIX_PEDIDO + reference + " entregado",
                PREFIX_TU_PEDIDO + reference + " quedó como entregado.\n"
                        + "Si aún no lo recibiste, respóndenos y lo revisamos.\n\n"
                        + "Nos ayudarías mucho contándonos qué te pareció.\n",
                EmailLayout.titled("Tu pedido quedó como entregado")
                        .highlight(LABEL_NUMERO_PEDIDO, reference)
                        .note("Si aún no llega", "Si aún no lo recibiste, responde este correo y lo revisamos.")
                        .paragraph("Nos ayudarías mucho contándonos qué te pareció.")
                        .build(),
                Map.of(KEY_ORDER_ID, orderId, KEY_REFERENCE, reference),
                orderId);
    }

    public NotificationMessage discountCodeAssigned(String code) {
        return new NotificationMessage(
                NotificationMessage.DISCOUNT_CODE_ASSIGNED,
                "Tienes un código de descuento",
                "Guardamos un código de descuento para tu próxima compra en Pilar Estilo: " + code + "\n\n"
                        + "Lo escribes en el carrito, en Código de descuento, antes de pagar.\n",
                EmailLayout.titled("Tienes un código de descuento")
                        .eyebrow("Solo para ti")
                        .paragraph("Guardamos este código para tu próxima compra en Pilar Estilo.")
                        .code(code, "Escríbelo en el carrito, en “Código de descuento”, antes de pagar.")
                        .build(),
                Map.of("code", code),
                null);
    }

    /** Tells a reviewer a receipt is waiting: which order, how much, and who paid. */
    public NotificationMessage paymentProofSubmitted(OrderView order, PaymentView payment, String buyerName) {
        String reference = order.publicReference();
        String amount = formatAmount(order.total().amount().toPlainString(), order.total().currency());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put("paymentId", payment.id());
        data.put("amount", order.total().amount().toPlainString());
        data.put(KEY_CURRENCY, order.total().currency());
        data.put("buyerName", buyerName);
        data.put("proofReference", payment.proofReference());

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
                order.id());
    }

    public NotificationMessage welcome(String fullName) {
        return welcome(fullName, null);
    }

    public NotificationMessage welcome(String fullName, WelcomeDiscount coupon) {
        StringBuilder body = new StringBuilder("Hola ").append(fullName).append(", bienvenida a Pilar Estilo.\n\n")
                .append("Ya puedes explorar el catálogo y hacer tu primera compra.\n");

        EmailLayout.Builder email = EmailLayout.titled("Bienvenida a Pilar Estilo")
                .eyebrow("Bienvenida")
                .paragraph("Hola " + fullName + ", gracias por crear tu cuenta.")
                .paragraph("Ya puedes recorrer el catálogo y guardar tus favoritos.");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", fullName);

        if (coupon != null) {
            String condition = couponCondition(coupon);
            body.append("\nTienes un código de bienvenida: ").append(coupon.code())
                    .append(" (").append(condition).append("), válido hasta ")
                    .append(coupon.validUntil()).append(".\n");
            email.paragraph("Como regalo de bienvenida, usa este código en tu primera compra:")
                    .code(coupon.code(), condition + " · válido hasta " + coupon.validUntil())
                    .route("Cómo usarlo", "Lo escribes en el carrito, en", "", "Código de descuento");
            data.put("welcomeDiscountCode", coupon.code());
            data.put("welcomeDiscountValidUntil", coupon.validUntil());
        } else {
            email.route("Empieza aquí", "Entra a", "pilarestilo.com", "Catálogo");
        }

        return new NotificationMessage(
                NotificationMessage.WELCOME,
                "Bienvenida a Pilar Estilo",
                body.toString(),
                email.build(),
                data,
                null);
    }

    private String couponCondition(WelcomeDiscount coupon) {
        String amount = "PERCENTAGE".equals(coupon.type())
                ? coupon.value().toPlainString() + "%"
                : formatAmount(coupon.value().toPlainString(), "CLP");
        return coupon.minOrderAmount() != null && coupon.minOrderAmount().signum() > 0
                ? amount + " de descuento en compras sobre " + formatAmount(coupon.minOrderAmount().toPlainString(), "CLP")
                : amount + " de descuento en tu próxima compra";
    }


    private String formatDeadline(Instant deadline) {
        var local = deadline.atZone(STORE_ZONE);
        var now = Instant.now().atZone(STORE_ZONE);
        return local.toLocalDate().equals(now.toLocalDate())
                ? local.format(TIME)
                : local.format(DATE_TIME);
    }

    /** Tells the buyer their boleta exists and what it says. */
    public NotificationMessage salesDocumentIssued(OrderView order, SalesDocumentView document) {
        String reference = order.publicReference();
        String currency = document.total().currency();
        boolean isCreditNote = document.isCreditNote();
        String documentName = switch (document.type()) {
            case "FACTURA" -> "factura";
            case "NOTA_CREDITO" -> "nota de crédito";
            case "BOLETA" -> "boleta";
            default -> "documento";
        };
        String opening = isCreditNote
                ? "Emitimos la nota de crédito que deja sin efecto tu pedido " + reference + "."
                : "Emitimos la " + documentName + " de tu pedido " + reference + ".";

        String body = opening + "\n\n"
                + "Folio: " + document.folio() + "\n"
                + "Neto: " + formatAmount(document.net().amount().toPlainString(), currency) + "\n"
                + "IVA (" + document.taxRate().toPlainString() + "%): "
                + formatAmount(document.tax().amount().toPlainString(), currency) + "\n"
                + "Total: " + formatAmount(document.total().amount().toPlainString(), currency) + "\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put("documentId", document.id());
        data.put("documentType", document.type());
        data.put("folio", document.folio());
        data.put("netAmount", document.net().amount());
        data.put("taxAmount", document.tax().amount());
        data.put("taxRate", document.taxRate());
        data.put(KEY_TOTAL_AMOUNT, document.total().amount());
        data.put(KEY_CURRENCY, currency);

        return new NotificationMessage(
                NotificationMessage.SALES_DOCUMENT_ISSUED,
                (isCreditNote ? "Nota de crédito " : "Boleta ") + document.folio()
                        + " de tu pedido " + reference,
                body,
                EmailLayout.titled(isCreditNote
                                ? "Tu nota de crédito está emitida"
                                : "Tu " + documentName + " está emitida")
                        .paragraph(opening)
                        .highlight("Folio", document.folio())
                        .details(List.of(
                                new String[]{"Neto", formatAmount(
                                        document.net().amount().toPlainString(), currency)},
                                new String[]{"IVA (" + document.taxRate().toPlainString() + "%)",
                                        formatAmount(document.tax().amount().toPlainString(), currency)},
                                new String[]{"Total", formatAmount(
                                        document.total().amount().toPlainString(), currency)}))
                        .build(),
                data,
                order.id());
    }

    /** The receipt of a return: what she asked for, and by when the law says the money is back. */
    public NotificationMessage returnRequested(OrderView order, ReturnView request) {
        String reference = order.publicReference();
        String deadline = formatDate(request.deadlineAt());
        boolean isRetracto = request.isRetracto();
        String opening = isRetracto
                ? "Recibimos tu arrepentimiento del pedido " + reference + "."
                : "Recibimos tu solicitud de devolución del pedido " + reference + ".";

        String body = opening + "\n\n"
                + "No necesitas justificarlo. Te escribiremos con los pasos para enviarnos la prenda; "
                + "el envío de vuelta lo pagamos nosotros.\n\n"
                + "Te devolvemos el dinero a más tardar el " + deadline + ".\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.id());
        data.put("kind", request.kind());
        data.put(KEY_REASON, request.reason());
        data.put(KEY_DEADLINE_AT, request.deadlineAt());

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
                order.id());
    }

    public NotificationMessage returnApproved(OrderView order, ReturnView request) {
        String reference = order.publicReference();
        String deadline = formatDate(request.deadlineAt());

        String body = "Aprobamos la devolución de tu pedido " + reference + ".\n\n"
                + "Coordinamos contigo el retiro o te enviamos la etiqueta de envío: el costo es "
                + "nuestro, no tuyo.\n\n"
                + "Apenas recibamos la prenda te devolvemos el dinero, y en todo caso antes del "
                + deadline + ".\n";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.id());
        data.put(KEY_DEADLINE_AT, request.deadlineAt());

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
                order.id());
    }

    public NotificationMessage refundRegistered(OrderView order, ReturnView request) {
        String reference = order.publicReference();
        String amount = request.refund() == null
                ? formatAmount(order.total().amount().toPlainString(), order.total().currency())
                : formatAmount(request.refund().amount().toPlainString(), request.refund().currency());
        String method = request.refundMethod() == null ? "—" : request.refundMethod();
        boolean hasReference = request.refundReference() != null
                && !request.refundReference().isBlank();

        StringBuilder body = new StringBuilder()
                .append("Te devolvimos el dinero de tu pedido ").append(reference).append(".\n\n")
                .append("Monto: ").append(amount).append('\n')
                .append("Medio: ").append(method).append('\n');
        if (hasReference) {
            body.append("Referencia: ").append(request.refundReference()).append('\n');
        }
        body.append("\nSegún tu banco puede tardar unos días en aparecer en tu cartola.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.id());
        data.put("refundAmount", request.refund() == null ? null : request.refund().amount());
        data.put("refundMethod", request.refundMethod());
        data.put("refundReference", request.refundReference());
        data.put("refundedAt", request.refundedAt());

        List<String[]> rows = hasReference
                ? List.of(new String[]{"Monto", amount},
                          new String[]{"Medio", method},
                          new String[]{"Referencia", request.refundReference()})
                : List.of(new String[]{"Monto", amount},
                          new String[]{"Medio", method});

        return new NotificationMessage(
                NotificationMessage.REFUND_REGISTERED,
                "Te devolvimos el dinero — pedido " + reference,
                body.toString(),
                EmailLayout.titled("Reembolso realizado")
                        .paragraph("Te devolvimos el dinero de tu pedido " + reference + ".")
                        .details(rows)
                        .note("Cuándo lo verás", "Según tu banco puede tardar unos días en aparecer en tu cartola.")
                        .build(),
                data,
                order.id());
    }

    /** Tells whoever manages returns that one just opened, and by when the money has to be back. */
    public NotificationMessage returnRequestedForStaff(OrderView order, ReturnView request,
                                                       String buyerName) {
        String reference = order.publicReference();
        String deadline = formatDate(request.deadlineAt());
        boolean isRetracto = request.isRetracto();

        String body = (isRetracto
                        ? "Una clienta se arrepintió de su pedido " + reference + "."
                        : "Se abrió una devolución del pedido " + reference + ".")
                + "\n\n"
                + "Cliente: " + buyerName + "\n"
                + "Motivo: " + request.reason() + "\n"
                + "Plazo legal para devolver el dinero: " + deadline + "\n\n"
                + (isRetracto
                        ? "Un retracto dentro de plazo no se rechaza. Revísalo en el panel, en Devoluciones.\n"
                        : "Revísalo en el panel, en Devoluciones.\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_ORDER_ID, order.id());
        data.put(KEY_ORDER_REFERENCE, reference);
        data.put(KEY_RETURN_ID, request.id());
        data.put("kind", request.kind());
        data.put(KEY_REASON, request.reason());
        data.put("buyerName", buyerName);
        data.put(KEY_DEADLINE_AT, request.deadlineAt());

        return new NotificationMessage(
                NotificationMessage.RETURN_REQUESTED_STAFF,
                (isRetracto ? "Retracto" : "Devolución") + " — pedido " + reference,
                body,
                null,
                data,
                order.id());
    }

    private String formatDate(Instant instant) {
        return instant == null ? "—" : DATE.format(instant.atZone(STORE_ZONE));
    }

    private String formatAmount(String amount, String currency) {
        return currency + " " + amount;
    }
}
