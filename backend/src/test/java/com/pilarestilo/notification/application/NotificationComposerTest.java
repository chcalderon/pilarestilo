package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.user.domain.events.UserRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NotificationComposerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String REFERENCE = "PE-3F9A2C71B4";

    private final NotificationComposer composer = new NotificationComposer();

    @Mock Payment payment;

    private Order order;

    @BeforeEach
    void setUp() {
        Money price = Money.of(BigDecimal.valueOf(45_000));
        order = Order.reconstruct(
                ORDER_ID, UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Vestido", price, 1)),
                price, Money.zero(), price,
                PaymentMethod.TRANSFER,
                "LOCAL", "starken", "Starken", "POR_PAGAR",
                UUID.randomUUID(), "Calle 1", null, SalesChannel.ECOMMERCE,
                OrderStatus.CREATED, Instant.now(), Instant.now(), REFERENCE);

        // Snapshot on the payment, deliberately different from any live settings value.
        lenient().when(payment.getTransferAccountHolderName()).thenReturn("Pilar Estilo SpA");
        lenient().when(payment.getTransferBankName()).thenReturn("Banco de Chile");
        lenient().when(payment.getTransferAccountType()).thenReturn("Cuenta Corriente");
        lenient().when(payment.getTransferAccountNumber()).thenReturn("00012345678");
        lenient().when(payment.getTransferAccountEmail()).thenReturn("pagos@pilarestilo.com");
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

    /** The reference is what ties a bank statement line back to an order. */
    @Test
    void transferInstructionsTellTheCustomerToQuoteTheReference() {
        var message = composer.transferInstructions(order, payment, null);

        assertThat(message.bodyText()).containsIgnoringCase("escribe " + REFERENCE);
    }

    /**
     * The single most misleading thing this message could say. AutoCancelPendingBankTransferUseCase
     * selects status = PENDING; making the transfer does not change that, uploading the proof does.
     */
    @Test
    void deadlineTellsTheCustomerToUploadProof_notMerelyToTransfer() {
        var message = composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800));

        assertThat(message.bodyText()).containsIgnoringCase("comprobante");
    }

    /** The real cancellation lands on the next cron tick, so the deadline must not be absolute. */
    @Test
    void deadlineIsPhrasedAsAFloor_notAGuillotine() {
        var message = composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800));

        assertThat(message.bodyText()).contains("puede cancelarse");
        assertThat(message.bodyText()).doesNotContain("será cancelado");
    }

    /** With auto-cancel off there is no deadline; printing one would be a lie. */
    @Test
    void omitsTheDeadlineParagraphWhenThereIsNoDeadline() {
        var message = composer.transferInstructions(order, payment, null);

        assertThat(message.bodyText())
                .doesNotContain("puede cancelarse")
                .containsIgnoringCase("comprobante");
        assertThat(message.data().get("deadlineAt")).isNull();
    }

    /** n8n forwards data verbatim, so a workflow can reword without a backend deploy. */
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
        var coupon = new UserRegistered.WelcomeDiscount(
                "BIENVENIDA-ABC123", "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO,
                java.time.LocalDate.now().plusDays(30));

        var message = composer.welcome("Camila Torres", coupon);

        assertThat(message.bodyText()).contains("BIENVENIDA-ABC123").contains("10%");
        assertThat(message.bodyHtml()).contains("BIENVENIDA-ABC123");
    }
}
