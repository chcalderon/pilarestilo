package com.pilarestilo.notificationservice;

import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReviewerReadPort;
import com.pilarestilo.notificationservice.domain.ports.ReturnReadPort;
import com.pilarestilo.notificationservice.domain.ports.SalesDocumentReadPort;
import com.pilarestilo.notificationservice.support.AbstractSharedStackIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drift guard. The {@code @SpringBootTest} bootstrap is the real assertion: the read-only
 * EntityManagerFactory runs {@code ddl-auto: validate} against the monolith's real schema (applied
 * by {@link AbstractSharedStackIT}), so a {@code *RoEntity} that maps a column the monolith has
 * renamed or dropped fails this test on the machine of whoever changed it — not in production.
 *
 * <p>The per-port calls below exercise each mapping against an empty table, catching a wrong column
 * name that {@code validate} might tolerate (e.g. a nullable column) but a {@code SELECT} would not.
 */
@SpringBootTest
class ReadOnlyMappingIT extends AbstractSharedStackIT {

    @Autowired OrderReadPort orderReadPort;
    @Autowired CustomerReadPort customerReadPort;
    @Autowired PaymentReadPort paymentReadPort;
    @Autowired SalesDocumentReadPort salesDocumentReadPort;
    @Autowired ReturnReadPort returnReadPort;
    @Autowired MessagingSettingsPort messagingSettingsPort;
    @Autowired PaymentReviewerReadPort paymentReviewerReadPort;

    @Test
    void every_read_only_mapping_selects_cleanly() {
        UUID missing = UUID.randomUUID();

        assertThat(orderReadPort.findById(missing)).isEmpty();
        assertThat(customerReadPort.findById(missing)).isEmpty();
        assertThat(paymentReadPort.findById(missing)).isEmpty();
        assertThat(salesDocumentReadPort.findById(missing)).isEmpty();
        assertThat(returnReadPort.findById(missing)).isEmpty();

        // The monolith migrations seed an admin user, so this list is non-empty; what matters is
        // that the query and the CustomerView mapping run cleanly and every row has an email.
        assertThat(paymentReviewerReadPort.findActiveByRoles(List.of("ADMIN", "ADMINISTRACION")))
                .isNotNull()
                .allSatisfy(reviewer -> assertThat(reviewer.email()).isNotBlank());
    }

    @Test
    void messaging_settings_are_read_from_the_seeded_row() {
        // V-series migrations seed the single system_settings row; the messaging columns may be
        // null but the row and its notification_providers column exist.
        assertThat(messagingSettingsPort.current()).isNotNull();
        assertThat(messagingSettingsPort.current().notificationProviders()).isNotNull();
    }
}
