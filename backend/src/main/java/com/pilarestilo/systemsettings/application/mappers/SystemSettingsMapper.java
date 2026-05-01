package com.pilarestilo.systemsettings.application.mappers;

import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;

public final class SystemSettingsMapper {

    private SystemSettingsMapper() {}

    public static SystemSettingsDto toDto(SystemSettings settings) {
        return new SystemSettingsDto(
                settings.getWhatsappNumber(),
                settings.getInstagramUrl(),
                settings.getFacebookUrl(),
                settings.getBankTransferAccountHolder(),
                settings.getBankTransferContactEmail(),
                settings.getBankTransferAccountNumber(),
                settings.getBankTransferBankName(),
                settings.getBankTransferAccountType(),
                settings.isPaymentMethodBankTransferEnabled(),
                settings.isPaymentMethodGatewayEnabled(),
                settings.getPaymentGatewayProviders().stream().map(Enum::name).toList(),
                settings.getPaymentGatewayMpApiBaseUrl(),
                settings.getPaymentGatewayMpSuccessUrl(),
                settings.getPaymentGatewayMpPendingUrl(),
                settings.getPaymentGatewayMpFailureUrl(),
                settings.getPaymentGatewayMpNotificationUrl(),
                settings.getPaymentGatewayMpAccessTokenEncrypted() != null && !settings.getPaymentGatewayMpAccessTokenEncrypted().isBlank(),
                settings.getPaymentGatewayMpWebhookTokenEncrypted() != null && !settings.getPaymentGatewayMpWebhookTokenEncrypted().isBlank(),
                settings.getMediaStorageProvider().name(),
                settings.getMediaS3Endpoint(),
                settings.getMediaS3Region(),
                settings.getMediaS3Bucket(),
                settings.getMediaS3AccessKeyId(),
                settings.getMediaS3SecretKeyEncrypted() != null && !settings.getMediaS3SecretKeyEncrypted().isBlank(),
                settings.isMediaS3PathStyleEnabled(),
                settings.getMediaS3PublicBaseUrl(),
                settings.getNotificationProvider().name(),
                settings.getN8nWebhookUrl(),
                settings.getN8nTokenHeaderName(),
                settings.getN8nApiKeyEncrypted() != null && !settings.getN8nApiKeyEncrypted().isBlank(),
                settings.getWhatsappSimulatedTo(),
                settings.getWhatsappSimulatedSender(),
                settings.getWhatsappTwilioApiBaseUrl(),
                settings.getWhatsappTwilioAccountSid(),
                settings.getWhatsappTwilioFrom(),
                settings.getWhatsappTwilioToFallback(),
                settings.getWhatsappTwilioSenderAlias(),
                settings.getWhatsappTwilioAuthTokenEncrypted() != null && !settings.getWhatsappTwilioAuthTokenEncrypted().isBlank(),
                settings.getSendgridApiBaseUrl(),
                settings.getSendgridFromEmail(),
                settings.getSendgridSenderName(),
                settings.getSendgridToFallback(),
                settings.getSendgridApiKeyEncrypted() != null && !settings.getSendgridApiKeyEncrypted().isBlank(),
                settings.getProductAiInferDefaultBrand(),
                settings.getProductAiInferDefaultCondition(),
                settings.getProductAiInferBasePrice(),
                settings.getProductAiInferListPriceMultiplier(),
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpUsername(),
                settings.getSmtpFromEmail(),
                settings.isSmtpAuthEnabled(),
                settings.isSmtpStarttlsEnabled(),
                settings.getSmtpPasswordEncrypted() != null && !settings.getSmtpPasswordEncrypted().isBlank(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy()
        );
    }

    public static PublicStoreSettingsDto toPublicDto(SystemSettings settings) {
        return new PublicStoreSettingsDto(
                settings.getWhatsappNumber(),
                settings.getInstagramUrl(),
                settings.getFacebookUrl(),
                resolveSupportEmail(settings),
                settings.getBankTransferAccountHolder(),
                settings.getBankTransferContactEmail(),
                settings.getBankTransferAccountNumber(),
                settings.getBankTransferBankName(),
                settings.getBankTransferAccountType(),
                settings.isPaymentMethodBankTransferEnabled(),
                settings.isPaymentMethodGatewayEnabled(),
                settings.getPaymentGatewayProviders().stream().map(Enum::name).toList()
        );
    }

    private static String resolveSupportEmail(SystemSettings settings) {
        String smtpFrom = settings.getSmtpFromEmail();
        if (smtpFrom != null && !smtpFrom.isBlank()) {
            return smtpFrom;
        }
        String sendgridFrom = settings.getSendgridFromEmail();
        if (sendgridFrom != null && !sendgridFrom.isBlank()) {
            return sendgridFrom;
        }
        return null;
    }
}
