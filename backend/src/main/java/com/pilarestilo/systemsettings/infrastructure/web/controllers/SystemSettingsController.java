package com.pilarestilo.systemsettings.infrastructure.web.controllers;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.systemsettings.application.commands.UpdateSystemSettingsCommand;
import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.application.usecases.GetPublicStoreSettingsUseCase;
import com.pilarestilo.systemsettings.application.usecases.GetSystemSettingsUseCase;
import com.pilarestilo.systemsettings.application.usecases.UpdateSystemSettingsUseCase;
import com.pilarestilo.systemsettings.infrastructure.web.requests.UpdateSystemSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-settings")
public class SystemSettingsController {

    private final GetSystemSettingsUseCase getSystemSettingsUseCase;
    private final UpdateSystemSettingsUseCase updateSystemSettingsUseCase;
    private final GetPublicStoreSettingsUseCase getPublicStoreSettingsUseCase;

    public SystemSettingsController(
            GetSystemSettingsUseCase getSystemSettingsUseCase,
            UpdateSystemSettingsUseCase updateSystemSettingsUseCase,
            GetPublicStoreSettingsUseCase getPublicStoreSettingsUseCase
    ) {
        this.getSystemSettingsUseCase = getSystemSettingsUseCase;
        this.updateSystemSettingsUseCase = updateSystemSettingsUseCase;
        this.getPublicStoreSettingsUseCase = getPublicStoreSettingsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsDto getAdminSettings() {
        return getSystemSettingsUseCase.execute();
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsDto updateSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        String updatedBy = currentUser == null ? "admin" : currentUser.email();
        return updateSystemSettingsUseCase.execute(new UpdateSystemSettingsCommand(
                request.whatsappNumber(),
                request.instagramUrl(),
                request.facebookUrl(),
                request.bankTransferAccountHolder(),
                request.bankTransferContactEmail(),
                request.bankTransferAccountNumber(),
                request.bankTransferBankName(),
                request.bankTransferAccountType(),
                request.paymentMethodBankTransferEnabled(),
                request.paymentMethodGatewayEnabled(),
                request.paymentGatewayProviders(),
                request.paymentGatewayMpApiBaseUrl(),
                request.paymentGatewayMpSuccessUrl(),
                request.paymentGatewayMpPendingUrl(),
                request.paymentGatewayMpFailureUrl(),
                request.paymentGatewayMpNotificationUrl(),
                request.paymentGatewayMpAccessToken(),
                request.clearPaymentGatewayMpAccessToken(),
                request.paymentGatewayMpWebhookToken(),
                request.clearPaymentGatewayMpWebhookToken(),
                request.mediaStorageProvider(),
                request.mediaS3Endpoint(),
                request.mediaS3Region(),
                request.mediaS3Bucket(),
                request.mediaS3AccessKeyId(),
                request.mediaS3SecretKey(),
                request.clearMediaS3SecretKey(),
                request.mediaS3PathStyleEnabled(),
                request.mediaS3PublicBaseUrl(),
                request.notificationProvider(),
                request.n8nWebhookUrl(),
                request.n8nTokenHeaderName(),
                request.n8nApiKey(),
                request.clearN8nApiKey(),
                request.whatsappSimulatedTo(),
                request.whatsappSimulatedSender(),
                request.whatsappTwilioApiBaseUrl(),
                request.whatsappTwilioAccountSid(),
                request.whatsappTwilioFrom(),
                request.whatsappTwilioToFallback(),
                request.whatsappTwilioSenderAlias(),
                request.whatsappTwilioAuthToken(),
                request.clearWhatsappTwilioAuthToken(),
                request.sendgridApiBaseUrl(),
                request.sendgridFromEmail(),
                request.sendgridSenderName(),
                request.sendgridToFallback(),
                request.sendgridApiKey(),
                request.clearSendgridApiKey(),
                request.productAiInferDefaultBrand(),
                request.productAiInferDefaultCondition(),
                request.productAiInferBasePrice(),
                request.productAiInferListPriceMultiplier(),
                request.smtpHost(),
                request.smtpPort(),
                request.smtpUsername(),
                request.smtpFromEmail(),
                request.smtpPassword(),
                request.clearSmtpPassword(),
                request.smtpAuthEnabled(),
                request.smtpStarttlsEnabled(),
                request.shippingZonesJson(),
                request.shippingCouriersJson(),
                request.shippingPaymentMode(),
                request.bankTransferAutoCancelEnabled(),
                request.bankTransferAutoCancelTimeoutMinutes(),
                request.bankTransferAutoCancelCron(),
                updatedBy
        ));
    }

    @GetMapping("/public")
    public PublicStoreSettingsDto getPublicSettings() {
        return getPublicStoreSettingsUseCase.execute();
    }
}
