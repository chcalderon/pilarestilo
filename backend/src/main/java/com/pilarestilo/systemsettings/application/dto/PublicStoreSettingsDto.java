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
        String shippingPaymentMode,
        /*
         * Exposed so the storefront can tell a customer how long they will have to upload
         * their transfer receipt *before* they commit to the order. The same window drives
         * the deadline in the confirmation email; a customer who only learns it afterwards
         * cannot factor it into the decision.
         */
        boolean bankTransferAutoCancelEnabled,
        int bankTransferAutoCancelTimeoutMinutes,
        /*
         * The versions the storefront is publishing right now. Exposed so the policy page shows the
         * same number the consent is stored against: a page that states one version while the
         * backend records another proves nothing, which is the whole point of storing it.
         */
        String privacyPolicyVersion,
        String termsVersion
) {}
