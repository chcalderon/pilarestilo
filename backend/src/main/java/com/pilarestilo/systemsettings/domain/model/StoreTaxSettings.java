package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.enums.TaxDocumentProvider;

import java.math.BigDecimal;

/**
 * The shop's own tax identity, plus the two rules that hang off it.
 *
 * <p>These ten values travel together everywhere: they are what a boleta prints about its issuer.
 * They arrive as a single argument rather than ten more positional parameters because
 * {@link SystemSettings#reconstruct} already takes sixty-two, and in a list that long a
 * {@code String} in the wrong slot compiles perfectly and files the giro under the comuna.
 *
 * @param payerRut                      RUT of the issuing taxpayer, as shown on the document
 * @param businessName                  razon social
 * @param businessActivity              giro
 * @param actecoCode                    SII economic activity code
 * @param vatRate                       percentage applied to new orders; snapshotted onto each one
 * @param documentRequiredBeforeDispatch when true, a paid order cannot be claimed for dispatch
 *                                      without a live document
 */
public record StoreTaxSettings(
        String payerRut,
        String businessName,
        String businessActivity,
        String actecoCode,
        String address,
        String commune,
        String city,
        BigDecimal vatRate,
        boolean documentRequiredBeforeDispatch,
        TaxDocumentProvider provider
) {

    public static final BigDecimal DEFAULT_VAT_RATE = TaxBreakdown.DEFAULT_RATE;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Empty identity with Chile's rate and the dispatch gate on. What a shop that has never opened
     * the Tributarios tab gets, and what {@code createDefault} starts from.
     */
    public static StoreTaxSettings empty() {
        return new StoreTaxSettings(null, null, null, null, null, null, null,
                DEFAULT_VAT_RATE, true, TaxDocumentProvider.MANUAL);
    }

    public static StoreTaxSettings of(
            String payerRut,
            String businessName,
            String businessActivity,
            String actecoCode,
            String address,
            String commune,
            String city,
            BigDecimal vatRate,
            Boolean documentRequiredBeforeDispatch,
            String provider
    ) {
        BigDecimal rate = vatRate == null ? DEFAULT_VAT_RATE : vatRate;
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(ONE_HUNDRED) > 0) {
            throw new DomainException("VAT rate must be between 0 and 100: " + rate.toPlainString());
        }
        return new StoreTaxSettings(
                SystemSettings.normalizeNullable(payerRut),
                SystemSettings.normalizeNullable(businessName),
                SystemSettings.normalizeNullable(businessActivity),
                SystemSettings.normalizeNullable(actecoCode),
                SystemSettings.normalizeNullable(address),
                SystemSettings.normalizeNullable(commune),
                SystemSettings.normalizeNullable(city),
                rate,
                // Absent means on. The gate is the thing that stops goods leaving undeclared, so a
                // missing value must not quietly disable it.
                documentRequiredBeforeDispatch == null || documentRequiredBeforeDispatch,
                TaxDocumentProvider.fromRaw(provider));
    }
}
