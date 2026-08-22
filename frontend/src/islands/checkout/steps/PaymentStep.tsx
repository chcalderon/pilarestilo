import { ArrowLeft, Building2, Clock, CreditCard } from 'lucide-react';
import type { CheckoutPaymentMethod } from '../../../lib/checkoutStore';
import type { BankTransferDetails } from '../useCheckoutConfig';
import type { Locale } from '../../../i18n/index';

interface Props {
  readonly locale: Locale;
  readonly method: CheckoutPaymentMethod;
  readonly transferEnabled: boolean;
  readonly gatewayEnabled: boolean;
  readonly gatewayLabel: string;
  readonly transfer: BankTransferDetails;
  /** Minutes to upload the receipt, or null when auto-cancel is off — then there is no deadline. */
  readonly transferWindowMinutes: number | null;
  readonly onSelect: (method: CheckoutPaymentMethod) => void;
  readonly onBack: () => void;
  readonly onContinue: () => void;
}

const copy = {
  es: {
    heading: 'Método de pago',
    transfer: 'Transferencia bancaria',
    transferHint: 'Te enviaremos los datos por correo al confirmar el pedido.',
    gateway: 'Pagar en línea',
    gatewayHint: 'Serás redirigido para completar el pago.',
    accountHolder: 'Titular',
    bank: 'Banco',
    accountType: 'Tipo de cuenta',
    accountNumber: 'N° de cuenta',
    email: 'Correo',
    back: 'Volver a envío',
    continue: 'Continuar a resumen',
    windowTitle: 'Plazo para transferir',
  },
  en: {
    heading: 'Payment method',
    transfer: 'Bank transfer',
    transferHint: 'We will email you the details once the order is confirmed.',
    gateway: 'Pay online',
    gatewayHint: 'You will be redirected to complete the payment.',
    accountHolder: 'Account holder',
    bank: 'Bank',
    accountType: 'Account type',
    accountNumber: 'Account number',
    email: 'Email',
    back: 'Back to shipping',
    continue: 'Continue to review',
    windowTitle: 'Time to transfer',
  },
} as const;

/**
 * Phrased as a floor, never a promise. The sweep that cancels unpaid transfers runs on a
 * cron, so the real cancellation lands at or after this window — telling the customer the
 * lower bound is the only figure that never over-promises.
 */
function windowText(minutes: number, locale: Locale): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const es = locale === 'es';

  const parts: string[] = [];
  if (hours > 0) {
    if (es) {
      const unit = hours === 1 ? 'hora' : 'horas';
      parts.push(`${hours} ${unit}`);
    } else {
      parts.push(`${hours}h`);
    }
  }
  if (rest > 0) parts.push(es ? `${rest} minutos` : `${rest}m`);
  const span = parts.join(es ? ' y ' : ' ');

  return es
    ? `Tendrás al menos ${span} para subir tu comprobante. Pasado ese plazo el pedido puede cancelarse y el stock se libera.`
    : `You will have at least ${span} to upload your receipt. After that the order may be cancelled and the stock released.`;
}

export default function PaymentStep({
  locale,
  method,
  transferEnabled,
  gatewayEnabled,
  gatewayLabel,
  transfer,
  transferWindowMinutes,
  onSelect,
  onBack,
  onContinue,
}: Props) {
  const l = copy[locale === 'es' ? 'es' : 'en'];

  const bankRows: Array<[string, string]> = [
    [l.accountHolder, transfer.accountHolder],
    [l.bank, transfer.bankName],
    [l.accountType, transfer.accountType],
    [l.accountNumber, transfer.accountNumber],
    [l.email, transfer.contactEmail],
  ].filter((row): row is [string, string] => Boolean(row[1]));

  return (
    <div className="bg-pe-white p-6">
      <h2 className="font-display text-pe-black text-xl font-semibold mb-6 flex items-center gap-2">
        <CreditCard size={18} className="text-pe-muted" aria-hidden="true" />
        {l.heading}
      </h2>

      <fieldset className="space-y-3 mb-6">
        <legend className="sr-only">{l.heading}</legend>

        {transferEnabled && (
          <label
            className={`flex items-start gap-3 p-4 border cursor-pointer transition-colors ${
              method === 'TRANSFER'
                ? 'border-pe-black bg-pe-cream/40'
                : 'border-pe-charcoal/20 hover:border-pe-charcoal/40'
            }`}
          >
            <input
              type="radio"
              name="checkout-payment"
              value="TRANSFER"
              checked={method === 'TRANSFER'}
              onChange={() => onSelect('TRANSFER')}
              className="mt-1 accent-pe-black w-4 h-4 shrink-0"
            />
            <span className="flex-1">
              <span className="flex items-center gap-2 font-sans text-sm font-semibold text-pe-black">
                <Building2 size={15} className="text-pe-muted" aria-hidden="true" />
                {l.transfer}
              </span>
              <span className="block font-sans text-[0.78rem] text-pe-muted mt-1">
                {l.transferHint}
              </span>

              {method === 'TRANSFER' && bankRows.length > 0 && (
                <span className="block mt-3 border-t border-pe-charcoal/15 pt-3 space-y-1">
                  {bankRows.map(([term, value]) => (
                    <span key={term} className="flex justify-between gap-3 font-sans text-[0.76rem]">
                      <span className="text-pe-muted">{term}</span>
                      <span className="text-pe-charcoal font-medium text-right break-all">{value}</span>
                    </span>
                  ))}
                </span>
              )}
            </span>
          </label>
        )}

        {gatewayEnabled && (
          <label
            className={`flex items-start gap-3 p-4 border cursor-pointer transition-colors ${
              method === 'WEBPAY'
                ? 'border-pe-black bg-pe-cream/40'
                : 'border-pe-charcoal/20 hover:border-pe-charcoal/40'
            }`}
          >
            <input
              type="radio"
              name="checkout-payment"
              value="WEBPAY"
              checked={method === 'WEBPAY'}
              onChange={() => onSelect('WEBPAY')}
              className="mt-1 accent-pe-black w-4 h-4 shrink-0"
            />
            <span className="flex-1">
              <span className="flex items-center gap-2 font-sans text-sm font-semibold text-pe-black">
                <CreditCard size={15} className="text-pe-muted" aria-hidden="true" />
                {l.gateway}
                {gatewayLabel && (
                  <span className="font-normal text-pe-muted">· {gatewayLabel}</span>
                )}
              </span>
              <span className="block font-sans text-[0.78rem] text-pe-muted mt-1">
                {l.gatewayHint}
              </span>
            </span>
          </label>
        )}
      </fieldset>

      {/* Only meaningful for a transfer, and only when the sweep is actually enabled. */}
      {method === 'TRANSFER' && transferWindowMinutes !== null && transferWindowMinutes > 0 && (
        <div className="flex items-start gap-3 border-l-2 border-pe-rose/50 bg-pe-cream/40 p-4 mb-6">
          <Clock size={16} className="text-pe-rose-ink shrink-0 mt-0.5" aria-hidden="true" />
          <div>
            <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal">
              {l.windowTitle}
            </p>
            <p className="font-sans text-[0.8rem] text-pe-charcoal mt-1">
              {windowText(transferWindowMinutes, locale)}
            </p>
          </div>
        </div>
      )}

      <div className="flex flex-col-reverse sm:flex-row gap-3">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center justify-center gap-2 min-h-12 px-5
            border border-pe-charcoal/25 font-sans text-[0.7rem] tracking-[0.16em] uppercase
            text-pe-charcoal hover:border-pe-black transition-colors
            focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
        >
          <ArrowLeft size={14} />
          {l.back}
        </button>
        <button
          type="button"
          onClick={onContinue}
          className="flex-1 sm:flex-none inline-flex items-center justify-center min-h-12 px-8
            bg-pe-black text-pe-white font-sans text-[0.7rem] tracking-[0.16em] uppercase
            focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
        >
          {l.continue}
        </button>
      </div>
    </div>
  );
}
