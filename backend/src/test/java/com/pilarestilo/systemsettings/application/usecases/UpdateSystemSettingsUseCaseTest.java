package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.application.commands.UpdateSystemSettingsCommand;
import com.pilarestilo.systemsettings.domain.events.BankTransferSettingsChangedEvent;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateSystemSettingsUseCaseTest {

    @Mock
    SystemSettingsRepository systemSettingsRepository;

    @Mock
    SystemSettingsCryptoService cryptoService;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    UpdateSystemSettingsUseCase useCase;

    /**
     * Builds a minimal valid command. Only gateway-method with MERCADO_PAGO is enabled
     * to satisfy payment-method validation without requiring bank-transfer fields.
     */
    private UpdateSystemSettingsCommand minimalCommand(
            Boolean bankTransferAutoCancelEnabled,
            Integer bankTransferAutoCancelTimeoutMinutes,
            String bankTransferAutoCancelCron
    ) {
        return new UpdateSystemSettingsCommand(
                "+56900000000",   // whatsappNumber (required)
                null, null,        // instagramUrl, facebookUrl
                null, null, null, null, null, // bank transfer display fields
                null,              // paymentMethodBankTransferEnabled → false
                true,              // paymentMethodGatewayEnabled
                List.of("MERCADO_PAGO"), // paymentGatewayProviders
                null, null, null, null, null, // mp base URLs
                null, null,        // mpAccessToken, clearMpAccessToken
                null, null,        // mpWebhookToken, clearMpWebhookToken
                null,              // mediaStorageProvider → LOCAL
                null, null, null, null, null, // S3 fields
                null, null, null,  // clearS3SecretKey, s3PathStyle, s3PublicBaseUrl
                null,              // notificationProvider → LOG
                null, null, null, null, // n8n fields
                null, null, null, null, null, null, null, null, null, // whatsapp fields
                null, null, null, null, null, null, // sendgrid fields
                null, null, null, null, // productAi fields
                null, null, null, null, null, null, null, null, // smtp fields
                null, null, null,  // shippingZonesJson, shippingCouriersJson, shippingPaymentMode
                bankTransferAutoCancelEnabled,
                bankTransferAutoCancelTimeoutMinutes,
                bankTransferAutoCancelCron,
                null, null, null, null, null, null, null, // tax identity: rut, razon social, giro, acteco, direccion, comuna, ciudad
                null, null, null,  // taxVatRate → 19.00, taxDocumentRequiredBeforeDispatch → true, taxDocumentProvider → MANUAL
                "test-user"
        );
    }

    private void setupRepository(SystemSettings settings) {
        when(systemSettingsRepository.get()).thenReturn(settings);
        when(systemSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void invalid_cron_throws_domain_exception() {
        when(systemSettingsRepository.get()).thenReturn(SystemSettings.createDefault());

        UpdateSystemSettingsCommand cmd = minimalCommand(true, 30, "NOT_A_VALID_CRON");

        assertThrows(DomainException.class, () -> useCase.execute(cmd));
    }

    @Test
    void event_published_when_enabled_changes_from_true_to_false() {
        SystemSettings settings = SystemSettings.createDefault();
        assertTrue(settings.isBankTransferAutoCancelEnabled());
        setupRepository(settings);

        useCase.execute(minimalCommand(false, 30, "0 */15 * * * *"));

        ArgumentCaptor<BankTransferSettingsChangedEvent> captor =
                ArgumentCaptor.forClass(BankTransferSettingsChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertFalse(captor.getValue().enabled());
    }

    @Test
    void event_published_when_cron_changes() {
        setupRepository(SystemSettings.createDefault());

        useCase.execute(minimalCommand(true, 30, "0 */5 * * * *"));

        verify(applicationEventPublisher).publishEvent(any(BankTransferSettingsChangedEvent.class));
    }

    @Test
    void event_published_when_timeout_changes() {
        setupRepository(SystemSettings.createDefault());

        useCase.execute(minimalCommand(true, 60, "0 */15 * * * *"));

        verify(applicationEventPublisher).publishEvent(any(BankTransferSettingsChangedEvent.class));
    }

    @Test
    void event_not_published_when_auto_cancel_fields_unchanged() {
        SystemSettings settings = SystemSettings.createDefault();
        setupRepository(settings);

        // Enabled=true, timeout=30, cron="0 */15 * * * *" — identical to defaults
        useCase.execute(minimalCommand(true, 30, "0 */15 * * * *"));

        verify(applicationEventPublisher, never()).publishEvent(any(BankTransferSettingsChangedEvent.class));
    }
}
