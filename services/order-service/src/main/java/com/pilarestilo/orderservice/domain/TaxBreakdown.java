package com.pilarestilo.orderservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The taxable base and the tax inside a gross amount.
 *
 * <p><strong>Twin of {@code com.pilarestilo.shared.application.TaxBreakdown} in the monolith.</strong>
 * Both codebases write the same {@code orders} row and share no compiler, so this arithmetic is
 * pinned on each side by the same test vectors: 45990 at 19% splits into 38647 and 7343, and 3 at
 * 100% splits into 2 and 1. If one side is changed the other has to change in the same commit.
 *
 * <p>The rate travels from the monolith, which owns system settings; the total is computed here, so
 * only the rate crosses the wire. The tax is derived by subtraction rather than a second
 * multiplication, which is what makes {@code net + tax = total} exact — the condition
 * {@code chk_sales_documents_amounts} asserts and a boleta requires.
 *
 * <p>Amounts round to whole units: CLP has no minor unit and the SII expects integer pesos.
 */
public record TaxBreakdown(BigDecimal net, BigDecimal tax, BigDecimal total, BigDecimal rate) {

    public static final BigDecimal DEFAULT_RATE = new BigDecimal("19.00");

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int UNIT_SCALE = 0;
    private static final int MONEY_SCALE = 2;

    public static TaxBreakdown fromGross(BigDecimal total, BigDecimal ratePercent) {
        if (total == null) {
            throw new IllegalArgumentException("Tax breakdown requires a total amount");
        }
        BigDecimal rate = ratePercent == null ? DEFAULT_RATE : ratePercent;
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100: " + rate.toPlainString());
        }

        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal net = total.divide(divisor, UNIT_SCALE, RoundingMode.HALF_UP)
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal scaledTotal = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new TaxBreakdown(net, scaledTotal.subtract(net), scaledTotal, rate);
    }
}
