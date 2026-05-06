package com.pilarestilo.systemsettings.application.dto;

import java.util.List;

public record PublicStoreSettingsDto(
        String whatsappNumber,
        String instagramUrl,
        String facebookUrl,
        String supportEmail,
        String bankTransferAccountHolder,
        String bankTransferContactEmail,
        String bankTransferAccountNumber,
        String bankTransferBankName,
        String bankTransferAccountType,
        boolean paymentMethodBankTransferEnabled,
        boolean paymentMethodGatewayEnabled,
        List<String> paymentGatewayProviders,
        String shippingZonesJson,
        String shippingCouriersJson,
        String shippingPaymentMode
) {}
