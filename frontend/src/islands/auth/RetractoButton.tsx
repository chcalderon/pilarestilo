import { useEffect, useState } from 'react';
import { Undo2 } from 'lucide-react';
import {
  getRetractoWindow,
  requestRetracto,
  type ReturnRequestDto,
} from '../../lib/api';

const copy = {
  es: {
    open: 'Me arrepiento',
    daysLeft: (days: number) => (days === 1 ? 'Queda 1 día' : `Quedan ${days} días`),
    title: 'Devolver este pedido',
    body:
      'Tienes 10 días desde que lo recibiste para arrepentirte, sin dar explicaciones. Te devolvemos todo lo pagado y el envío de vuelta lo pagamos nosotros.',
    reasonLabel: 'Cuéntanos por qué (opcional para nosotros, útil para mejorar)',
    placeholder: 'No me quedó como esperaba…',
    confirm: 'Confirmar devolución',
    cancel: 'Cancelar',
    sending: 'Enviando…',
    done: 'Recibimos tu solicitud. Te escribiremos con los pasos para enviarnos la prenda.',
    already: 'Ya tienes una devolución en curso para este pedido.',
  },
  en: {
    open: 'I changed my mind',
    daysLeft: (days: number) => (days === 1 ? '1 day left' : `${days} days left`),
    title: 'Return this order',
    body:
      'You have 10 days from receiving it to withdraw, with no explanation needed. We refund everything you paid and cover the return shipping.',
    reasonLabel: 'Tell us why (optional for you, useful for us)',
    placeholder: 'It was not what I expected…',
    confirm: 'Confirm return',
    cancel: 'Cancel',
    sending: 'Sending…',
    done: 'We got your request. We will write with the steps to send the garment back.',
    already: 'You already have a return in progress for this order.',
  },
} as const;

interface Props {
  readonly orderId: string;
  readonly token: string;
  readonly locale: 'es' | 'en';
  /** Returns already opened by this customer, so an order in progress does not offer the button. */
  readonly existing: ReturnRequestDto | null;
  readonly onRequested: (created: ReturnRequestDto) => void;
}

/**
 * The boton de arrepentimiento the Ley 21.398 asks for.
 *
 * <p>It only appears while the ten-day window is genuinely open — the backend decides when that is
 * and the component asks. Offering a button that fails on click is worse than not offering one, and
 * the days remaining are shown so the customer is not guessing.
 */
export default function RetractoButton({ orderId, token, locale, existing, onRequested }: Props) {
  const l = copy[locale];
  const [closesAt, setClosesAt] = useState<Date | null>(null);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getRetractoWindow(orderId, token).then((iso) => {
      if (!cancelled && iso) setClosesAt(new Date(iso));
    });
    return () => {
      cancelled = true;
    };
  }, [orderId, token]);

  if (existing) {
    return (
      <p className="font-sans text-[0.72rem] text-pe-muted">{l.already}</p>
    );
  }
  if (!closesAt) return null;

  const daysLeft = Math.ceil((closesAt.getTime() - Date.now()) / 86_400_000);
  if (daysLeft <= 0) return null;

  async function submit() {
    setBusy(true);
    setError('');
    try {
      const created = await requestRetracto(orderId, reason.trim() || 'Retracto', token);
      setOpen(false);
      onRequested(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo enviar la solicitud');
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 px-3 py-2 border border-pe-charcoal/25
          font-sans text-[0.66rem] tracking-wider uppercase text-pe-charcoal
          hover:border-pe-black transition-colors
          focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
      >
        <Undo2 size={13} aria-hidden="true" />
        {l.open}
        <span className="opacity-55 normal-case tracking-normal">· {l.daysLeft(daysLeft)}</span>
      </button>
    );
  }

  return (
    <div className="w-full border border-pe-charcoal/15 p-3 space-y-2">
      <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal">
        {l.title}
      </p>
      <p className="font-sans text-[0.78rem] leading-relaxed text-pe-charcoal max-w-[62ch]">
        {l.body}
      </p>
      <label className="block space-y-1">
        <span className="font-sans text-[0.7rem] text-pe-muted">{l.reasonLabel}</span>
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={l.placeholder}
          className="w-full border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem]
            text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
        />
      </label>
      {error && <p className="font-sans text-[0.72rem] text-red-500">{error}</p>}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => void submit()}
          disabled={busy}
          className="inline-flex items-center px-3 py-2 bg-pe-black text-pe-white
            font-sans text-[0.66rem] tracking-wider uppercase disabled:opacity-60"
        >
          {busy ? l.sending : l.confirm}
        </button>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="inline-flex items-center px-3 py-2 border border-pe-charcoal/25
            font-sans text-[0.66rem] tracking-wider uppercase text-pe-charcoal"
        >
          {l.cancel}
        </button>
      </div>
    </div>
  );
}
