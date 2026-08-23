package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * This adapter stands in for WhatsApp where messaging a real person would be wrong, so the one
 * thing it must never do is address the customer. An earlier version logged
 * {@code recipient.phone()} and ignored the configured destination entirely, which left the
 * simulated-to and sender-alias settings read by nothing.
 */
@ExtendWith(MockitoExtension.class)
class SimulatedWhatsAppNotificationSenderTest {

    private static final String CUSTOMER_PHONE = "+56911112222";

    @Mock SystemSettingsRepository systemSettingsRepository;
    @Mock SystemSettings settings;

    private ListAppender<ILoggingEvent> appender;
    private SimulatedWhatsAppNotificationSender sender;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingsRepository.get()).thenReturn(settings);
        lenient().when(settings.getWhatsappSimulatedTo()).thenReturn("+56999999999");
        lenient().when(settings.getWhatsappSimulatedSender()).thenReturn("Bandeja de pruebas");

        sender = new SimulatedWhatsAppNotificationSender(
                systemSettingsRepository, "+56900000000", "Pilar Estilo");

        Logger logger = (Logger) LoggerFactory.getLogger(SimulatedWhatsAppNotificationSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    private String loggedLine() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    }

    private NotificationMessage message() {
        return new NotificationMessage("ORDER_SHIPPED", "Pedido enviado", "Tu pedido va en camino.",
                null, Map.of(), UUID.randomUUID());
    }

    private NotificationRecipient whatsappCustomer() {
        return NotificationRecipient.of(CUSTOMER_PHONE, "cliente@example.com", "WHATSAPP");
    }

    @Test
    void addressesTheConfiguredInboxRatherThanTheCustomer() {
        sender.send(message(), whatsappCustomer());

        String line = loggedLine();
        assertThat(line)
                .contains("to=+56999999999")
                .doesNotContain("to=" + CUSTOMER_PHONE);
    }

    @Test
    void usesTheConfiguredSenderAlias() {
        sender.send(message(), whatsappCustomer());

        assertThat(loggedLine()).contains("sender=Bandeja de pruebas");
    }

    /** Settings win over the environment default, which is only the fallback. */
    @Test
    void fallsBackToTheEnvironmentWhenSettingsAreBlank() {
        lenient().when(settings.getWhatsappSimulatedTo()).thenReturn("  ");
        lenient().when(settings.getWhatsappSimulatedSender()).thenReturn(null);

        sender.send(message(), whatsappCustomer());

        assertThat(loggedLine()).contains("to=+56900000000").contains("sender=Pilar Estilo");
    }

    @Test
    void carriesTheComposedBody() {
        sender.send(message(), whatsappCustomer());

        assertThat(loggedLine()).contains("Tu pedido va en camino.");
    }

    @Test
    void skipsWhenTheCustomerDoesNotWantWhatsApp() {
        sender.send(message(), NotificationRecipient.of(CUSTOMER_PHONE, "c@example.com", "EMAIL"));

        String line = loggedLine();
        assertThat(line)
                .contains("skipped")
                .doesNotContain("to=");
    }

    @Test
    void neverLogsTheCustomerPhoneAsTheDestination() {
        sender.send(message(), whatsappCustomer());

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).isNotEmpty().allSatisfy(l -> assertThat(l).doesNotContain("to=" + CUSTOMER_PHONE));
    }
}
