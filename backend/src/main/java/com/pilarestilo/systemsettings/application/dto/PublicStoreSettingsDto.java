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
         * False when order writes are delegated to order-service, which has no redemption
         * ledger and rejects any order carrying a code. The storefront hides the input rather
         * than letting a customer type a code, watch it validate, see the total drop, and
         * then have the order refused at the last step.
         */
        boolean discountCodesEnabled
) {}
