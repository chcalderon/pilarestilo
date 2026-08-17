package com.pilarestilo.shared.application;

import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The taxable base and the tax inside a gross amount.
 *
 * <p>Chilean consumer prices are quoted VAT-inclusive, so the total is the figure the customer
 * agreed to and everything else is derived from it. A boleta reports {@code MntNeto}, {@code IVA}
 * and {@code MntTotal}, and those three have to reconcile exactly.
 *
 * <p><strong>The tax is a subtraction, never a second multiplication.</strong> Rounding the net and
 * then rounding {@code net * rate} independently produces pairs that miss the total by a peso, which
 * is the same class of defect that a discount already caused in this codebase. Deriving the tax as
 * the remainder makes {@code net + tax == total} true by construction, which is what
 * {@code chk_sales_documents_amounts} asserts in the database.
 *
 * <p>Amounts round to whole units because the SII expects integer pesos; CLP has no minor unit.
 *
 * <p>This is the single implementation of the rule. {@code order-service} writes the same three
 * columns but receives them already computed rather than repeating the arithmetic: two copies of a
 * rounding rule across two codebases that share no compiler is how five bugs got here.
 */
public record TaxBreakdown(Money net, Money tax, Money total, BigDecimal rate) {

    /**
     * Chile's IVA. The one place the number is written down: settings default to it and order
     * factories fall back to it, so raising it is a single edit rather than a hunt.
     */
    public static final BigDecimal DEFAULT_RATE = new BigDecimal("19.00");

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int UNIT_SCALE = 0;

    public static TaxBreakdown fromGross(Money total, BigDecimal ratePercent) {
        if (total == null) {
            throw new DomainException("Tax breakdown requires a total amount");
        }
        if (ratePercent == null) {
            throw new DomainException("Tax breakdown requires a rate");
        }
        if (ratePercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Tax rate cannot be negative: " + ratePercent.toPlainString());
        }
        if (ratePercent.compareTo(ONE_HUNDRED) > 0) {
            throw new DomainException("Tax rate cannot exceed 100%: " + ratePercent.toPlainString());
        }

        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal netAmount = total.amount().divide(divisor, UNIT_SCALE, RoundingMode.HALF_UP);

        Money net = Money.of(netAmount, total.currency());
        return new TaxBreakdown(net, total.subtract(net), total, ratePercent);
    }
}
