package com.pilarestilo.returns.domain.model;

import com.pilarestilo.shared.domain.DomainException;

/**
 * Where a transfer refund is sent.
 *
 * <p>Asked for when the return is opened, never at checkout: the great majority of purchases are
 * never returned, and the Ley 21.719 asks for no more data than the purpose needs. Collecting an
 * account number from every buyer would be data the shop does not need and a breach surface it does
 * not want.
 *
 * <p>The account number arrives already encrypted — the domain never sees it in clear, the same way
 * {@code SystemSettings} never holds a clear SMTP password. What survives after the refund settles
 * is {@code last4} and the operation reference, because those are what identify the payment
 * afterwards; the number itself is erased.
 */
public record RefundAccount(
        String holder,
        String rut,
        String bankName,
        String accountType,
        String numberEncrypted,
        String last4
) {

    public static RefundAccount of(String holder, String rut, String bankName, String accountType,
                                   String numberEncrypted, String last4) {
        if (isBlank(holder) || isBlank(rut) || isBlank(bankName) || isBlank(accountType)) {
            throw new DomainException("A transfer refund needs the account holder, RUT, bank and type");
        }
        if (isBlank(numberEncrypted)) {
            throw new DomainException("A transfer refund needs the account number");
        }
        return new RefundAccount(holder.trim(), rut.trim(), bankName.trim(), accountType.trim(),
                numberEncrypted, last4);
    }

    /** What is left once the money has moved: enough to recognise the account, not to use it. */
    public RefundAccount erased() {
        return new RefundAccount(holder, rut, bankName, accountType, null, last4);
    }

    public boolean isConfigured() {
        return numberEncrypted != null && !numberEncrypted.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
