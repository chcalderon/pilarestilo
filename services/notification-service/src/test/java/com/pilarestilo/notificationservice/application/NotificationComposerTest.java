package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.OrderView.OrderItemView;
import com.pilarestilo.notificationservice.domain.view.PaymentView;
import com.pilarestilo.notificationservice.domain.view.WelcomeDiscount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationComposerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String REFERENCE = "PE-3F9A2C71B4";

    private final NotificationComposer composer = new NotificationComposer();

    private OrderView order;
    private PaymentView payment;

    @BeforeEach
    void setUp() {
        Money price = Money.of(BigDecimal.valueOf(45_000), "CLP");
        order = new OrderView(
                ORDER_ID, REFERENCE, UUID.randomUUID(), "CREATED",
                price, Money.of(BigDecimal.ZERO, "CLP"), price, Money.of(BigDecimal.ZERO, "CLP"),
                BigDecimal.ZERO, price,
                "starken", "Starken", "LOCAL",
                List.of(new OrderItemView("Vestido", null, null, 1, price)));

        payment = new PaymentView(
                UUID.randomUUID(), ORDER_ID, "TRANSFER", "REGISTERED", null, null, Instant.now(),
                "Pilar Estilo SpA", "Banco de Chile", "Cuenta Corriente", "00012345678",
                "pagos@pilarestilo.com");
    }

    @Test
    void transferInstructionsCarryEverythingNeededToPay() {
        var message = composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800));

        assertThat(message.templateKey()).isEqualTo(NotificationMessage.TRANSFER_INSTRUCTIONS);
        assertThat(message.subject()).contains(REFERENCE);
        assertThat(message.bodyText())
                .contains(REFERENCE)
                .contains("45000")
                .contains("Pilar Estilo SpA")
                .contains("Banco de Chile")
                .contains("Cuenta Corriente")
                .contains("00012345678");
    }

    @Test
    void transferInstructionsTellTheCustomerToQuoteTheReference() {
        var message = composer.transferInstructions(order, payment, null);

        assertThat(message.bodyText()).containsIgnoringCase("escribe " + REFERENCE);
    }

    @Test
    void deadlineTellsTheCustomerToUploadProof_notMerelyToTransfer() {
        var message = composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800));

        assertThat(message.bodyText()).containsIgnoringCase("comprobante");
    }

    @Test
    void deadlineIsPhrasedAsAFloor_notAGuillotine() {
        var message = composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800));

        assertThat(message.bodyText()).contains("puede cancelarse");
        assertThat(message.bodyText()).doesNotContain("será cancelado");
    }

    @Test
    void omitsTheDeadlineParagraphWhenThereIsNoDeadline() {
        var message = composer.transferInstructions(order, payment, null);

        assertThat(message.bodyText())
                .doesNotContain("puede cancelarse")
                .containsIgnoringCase("comprobante");
        assertThat(message.data().get("deadlineAt")).isNull();
    }

    @Test
    void structuredDataMirrorsTheCopy() {
        Instant deadline = Instant.now().plusSeconds(1800);
        var message = composer.transferInstructions(order, payment, deadline);

        assertThat(message.data())
                .containsEntry("orderReference", REFERENCE)
                .containsEntry("bankAccountNumber", "00012345678")
                .containsEntry("bankName", "Banco de Chile")
                .containsEntry("deadlineAt", deadline);
        assertThat(message.data().get("deadlineLocal")).isNotNull();
    }

    @Test
    void cancellationNamesTheOrderAndTheReason() {
        var message = composer.orderCancelled(order, "Cierre por sistema: comprobante no recibido");

        assertThat(message.templateKey()).isEqualTo(NotificationMessage.ORDER_CANCELLED);
        assertThat(message.subject()).contains(REFERENCE);
        assertThat(message.bodyText())
                .contains(REFERENCE)
                .contains("comprobante no recibido");
        assertThat(message.data()).containsEntry("orderReference", REFERENCE);
    }

    @Test
    void cancellationReadsCleanlyWithoutAReason() {
        var message = composer.orderCancelled(order, null);

        assertThat(message.bodyText()).contains(REFERENCE).doesNotContain("Motivo:");
    }

    @Test
    void orderConfirmationListsItemsAndTheRetractoRight() {
        var message = composer.orderConfirmation(order);

        assertThat(message.templateKey()).isEqualTo(NotificationMessage.ORDER_CONFIRMATION);
        assertThat(message.subject()).contains(REFERENCE);
        assertThat(message.bodyText())
                .contains("Vestido")
                .contains(REFERENCE)
                .contains("10 días");
        assertThat(message.bodyHtml())
                .contains("Pedido " + REFERENCE)
                .contains("Vestido")
                .contains("Mi cuenta")
                .doesNotContain("<a ").doesNotContain("href=");
    }

    @Test
    void salesDocumentCreditNoteReadsAsUndoingTheSale() {
        var creditNote = new com.pilarestilo.notificationservice.domain.view.SalesDocumentView(
                UUID.randomUUID(), "NOTA_CREDITO", "F-12", Money.of(BigDecimal.valueOf(37815), "CLP"),
                Money.of(BigDecimal.valueOf(7185), "CLP"), BigDecimal.valueOf(19),
                Money.of(BigDecimal.valueOf(45000), "CLP"));

        var message = composer.salesDocumentIssued(order, creditNote);

        assertThat(message.subject()).contains("Nota de crédito").contains("F-12");
        assertThat(message.bodyText()).contains("deja sin efecto");
    }

    @Test
    void welcomeGreetsTheNewCustomerByName() {
        var message = composer.welcome("Camila Torres");

        assertThat(message.templateKey()).isEqualTo(NotificationMessage.WELCOME);
        assertThat(message.bodyText()).contains("Camila Torres");
        assertThat(message.bodyHtml()).contains("Camila Torres");
    }

    @Test
    void welcomeWithoutACouponHasNoCodeInIt() {
        var message = composer.welcome("Camila Torres", null);

        assertThat(message.bodyText()).doesNotContain("BIENVENIDA-");
    }

    @Test
    void welcomeWithACouponNamesTheCodeAndItsConditions() {
        var coupon = new WelcomeDiscount("BIENVENIDA-ABC123", "PERCENTAGE", BigDecimal.TEN,
                BigDecimal.ZERO, LocalDate.now().plusDays(30));

        var message = composer.welcome("Camila Torres", coupon);

        assertThat(message.bodyText()).contains("BIENVENIDA-ABC123").contains("10%");
        assertThat(message.bodyHtml())
                .contains("BIENVENIDA-ABC123")
                .contains("Código de descuento")
                .doesNotContain("<a ").doesNotContain("href=");
    }

    @Test
    void welcomeWithoutACouponPointsAtTheCatalogue() {
        var message = composer.welcome("Camila Torres", null);
        assertThat(message.bodyHtml())
                .contains("Catálogo")
                .doesNotContain("<a ").doesNotContain("href=");
    }

    @Test
    void discountCodeAssignedNamesTheCodeInHtml() {
        var message = composer.discountCodeAssigned("VUELVE15");
        assertThat(message.bodyText()).contains("VUELVE15");
        assertThat(message.bodyHtml())
                .isNotNull()
                .contains("VUELVE15")
                .contains("Código de descuento")
                .doesNotContain("<a ").doesNotContain("href=");
    }
}
