package com.pilarestilo.billing.infrastructure;

import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.billing.infrastructure.adapters.SalesDocumentGateAdapter;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.systemsettings.domain.model.PolicyVersions;
import com.pilarestilo.systemsettings.domain.model.StoreTaxSettings;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rule that keeps undeclared goods from leaving. Its two inputs are the shop's switch and
 * whether the order has a live document, and getting either backwards ships parcels with no boleta.
 */
@ExtendWith(MockitoExtension.class)
class SalesDocumentGateAdapterTest {

    @Mock
    SalesDocumentRepository salesDocumentRepository;
    @Mock
    SystemSettingsRepository systemSettingsRepository;

    private final UUID orderId = UUID.randomUUID();

    @Test
    void blocks_a_paid_order_with_no_document() {
        givenGateEnabled(true);
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.empty());

        assertTrue(gate().blocksDispatch(orderId));
    }

    @Test
    void lets_through_an_order_whose_document_is_live() {
        givenGateEnabled(true);
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.of(document()));

        assertFalse(gate().blocksDispatch(orderId));
    }

    /** With the rule switched off nothing is even looked up: the shop has opted out. */
    @Test
    void lets_everything_through_when_the_shop_switched_the_rule_off() {
        givenGateEnabled(false);

        assertFalse(gate().blocksDispatch(orderId));
        verify(salesDocumentRepository, never()).findLiveByOrderId(orderId);
    }

    private SalesDocumentGateAdapter gate() {
        return new SalesDocumentGateAdapter(salesDocumentRepository, systemSettingsRepository);
    }

    private void givenGateEnabled(boolean enabled) {
        SystemSettings settings = SystemSettings.createDefault();
        settings.update(
                "+56900000000", null, null,
                null, null, null, null, null,
                false, true, "MERCADO_PAGO",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null,
                null, null, null, null, null, true, true,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null,
                true, 30, "0 */15 * * * *",
                StoreTaxSettings.of(null, null, null, null, null, null, null,
                        new BigDecimal("19.00"), enabled, "MANUAL"),
                com.pilarestilo.systemsettings.domain.model.WelcomeDiscountSettings.disabled(),
                PolicyVersions.initial(),
                "test");
        when(systemSettingsRepository.get()).thenReturn(settings);
    }

    private SalesDocument document() {
        return SalesDocument.issue(orderId, SalesDocumentType.BOLETA, "1042",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                null, null, null, null, null, null, UUID.randomUUID(), null);
    }
}
