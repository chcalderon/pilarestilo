package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.commands.UpdateSystemSettingsCommand;
import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.systemsettings.domain.events.BankTransferSettingsChangedEvent;
import com.pilarestilo.systemsettings.domain.model.StoreTaxSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UpdateSystemSettingsUseCase {

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public UpdateSystemSettingsUseCase(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PUBLIC_STORE_SETTINGS, allEntries = true)
    public SystemSettingsDto execute(UpdateSystemSettingsCommand command) {
        var settings = systemSettingsRepository.get();

        String nextSmtpPassword = resolveEncryptedSecret(
                settings.getSmtpPasswordEncrypted(),
                command.smtpPassword(),
                command.clearSmtpPassword()
        );
        String nextTwilioAuthToken = resolveEncryptedSecret(
                settings.getWhatsappTwilioAuthTokenEncrypted(),
                command.whatsappTwilioAuthToken(),
                command.clearWhatsappTwilioAuthToken()
        );
        String nextSendgridApiKey = resolveEncryptedSecret(
                settings.getSendgridApiKeyEncrypted(),
                command.sendgridApiKey(),
                command.clearSendgridApiKey()
        );
        String nextMediaS3SecretKey = resolveEncryptedSecret(
                settings.getMediaS3SecretKeyEncrypted(),
                command.mediaS3SecretKey(),
                command.clearMediaS3SecretKey()
        );
        String nextPaymentGatewayMpAccessToken = resolveEncryptedSecret(
                settings.getPaymentGatewayMpAccessTokenEncrypted(),
                command.paymentGatewayMpAccessToken(),
                command.clearPaymentGatewayMpAccessToken()
        );
        String nextPaymentGatewayMpWebhookToken = resolveEncryptedSecret(
                settings.getPaymentGatewayMpWebhookTokenEncrypted(),
                command.paymentGatewayMpWebhookToken(),
                command.clearPaymentGatewayMpWebhookToken()
        );
        String nextN8nApiKey = resolveEncryptedSecret(
                settings.getN8nApiKeyEncrypted(),
                command.n8nApiKey(),
                command.clearN8nApiKey()
        );

        boolean oldEnabled = settings.isBankTransferAutoCancelEnabled();
        int oldTimeout = settings.getBankTransferAutoCancelTimeoutMinutes();
        String oldCron = settings.getBankTransferAutoCancelCron();

        settings.update(
                command.whatsappNumber(),
                command.instagramUrl(),
                command.facebookUrl(),
                command.bankTransferAccountHolder(),
                command.bankTransferContactEmail(),
                command.bankTransferAccountNumber(),
                command.bankTransferBankName(),
                command.bankTransferAccountType(),
                Boolean.TRUE.equals(command.paymentMethodBankTransferEnabled()),
                Boolean.TRUE.equals(command.paymentMethodGatewayEnabled()),
                serializePaymentGatewayProviders(command.paymentGatewayProviders()),
                command.paymentGatewayMpApiBaseUrl(),
                nextPaymentGatewayMpAccessToken,
                command.paymentGatewayMpSuccessUrl(),
                command.paymentGatewayMpPendingUrl(),
                command.paymentGatewayMpFailureUrl(),
                command.paymentGatewayMpNotificationUrl(),
                nextPaymentGatewayMpWebhookToken,
                command.mediaStorageProvider(),
                command.mediaS3Endpoint(),
                command.mediaS3Region(),
                command.mediaS3Bucket(),
                command.mediaS3AccessKeyId(),
                nextMediaS3SecretKey,
                Boolean.TRUE.equals(command.mediaS3PathStyleEnabled()),
                command.mediaS3PublicBaseUrl(),
                command.smtpHost(),
                command.smtpPort(),
                command.smtpUsername(),
                command.smtpFromEmail(),
                nextSmtpPassword,
                Boolean.TRUE.equals(command.smtpAuthEnabled()),
                Boolean.TRUE.equals(command.smtpStarttlsEnabled()),
                serializeProviderList(command.notificationProviders()),
                command.n8nWebhookUrl(),
                command.n8nTokenHeaderName(),
                nextN8nApiKey,
                command.whatsappSimulatedTo(),
                command.whatsappSimulatedSender(),
                command.whatsappTwilioApiBaseUrl(),
                command.whatsappTwilioAccountSid(),
                nextTwilioAuthToken,
                command.whatsappTwilioFrom(),
                command.whatsappTwilioToFallback(),
                command.whatsappTwilioSenderAlias(),
                command.sendgridApiBaseUrl(),
                nextSendgridApiKey,
                command.sendgridFromEmail(),
                command.sendgridSenderName(),
                command.sendgridToFallback(),
                command.productAiInferDefaultBrand(),
                command.productAiInferDefaultCondition(),
                command.productAiInferBasePrice(),
                command.productAiInferListPriceMultiplier(),
                command.shippingZonesJson(),
                command.shippingCouriersJson(),
                command.shippingPaymentMode(),
                command.bankTransferAutoCancelEnabled(),
                command.bankTransferAutoCancelTimeoutMinutes(),
                command.bankTransferAutoCancelCron(),
                StoreTaxSettings.of(
                        command.taxPayerRut(),
                        command.taxBusinessName(),
                        command.taxBusinessActivity(),
                        command.taxActecoCode(),
                        command.taxAddress(),
                        command.taxCommune(),
                        command.taxCity(),
                        command.taxVatRate(),
                        command.taxDocumentRequiredBeforeDispatch(),
                        command.taxDocumentProvider()),
                /*
                 * Carried through untouched: publishing a new version of the privacy policy or the
                 * terms is a deliberate act with a text behind it, not a field on the settings
                 * form. Every consent already stored points at the version it was given under, and
                 * bumping this by accident would silently claim they were given under the new one.
                 */
                settings.getPolicyVersions(),
                command.updatedBy()
        );

        var saved = systemSettingsRepository.save(settings);

        if (saved.isBankTransferAutoCancelEnabled() != oldEnabled
                || saved.getBankTransferAutoCancelTimeoutMinutes() != oldTimeout
                || !saved.getBankTransferAutoCancelCron().equals(oldCron)) {
            applicationEventPublisher.publishEvent(new BankTransferSettingsChangedEvent(
                    saved.isBankTransferAutoCancelEnabled(),
                    saved.getBankTransferAutoCancelCron(),
                    saved.getBankTransferAutoCancelTimeoutMinutes()
            ));
        }

        return SystemSettingsMapper.toDto(saved);
    }

    /** Same joining the gateways use; kept as one helper so the two cannot drift. */
    private String serializeProviderList(List<String> providers) {
        return serializePaymentGatewayProviders(providers);
    }

    private String serializePaymentGatewayProviders(List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            return "";
        }
        return providers.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(","));
    }

    private String resolveEncryptedSecret(String currentEncrypted, String nextPlainText, Boolean clearFlag) {
        if (Boolean.TRUE.equals(clearFlag)) {
            return null;
        }
        if (nextPlainText != null && !nextPlainText.isBlank()) {
            return cryptoService.encrypt(nextPlainText.trim());
        }
        return currentEncrypted;
    }
}
