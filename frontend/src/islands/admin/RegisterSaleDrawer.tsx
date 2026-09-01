import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Search, Trash2, X } from 'lucide-react';
import Overlay from './Overlay';
import {
  registerExternalSale,
  searchProducts,
  type ExternalSaleRequest,
  type ProductDto,
  type ProductVariantDto,
} from '../../lib/api';

interface Props {
  readonly token: string;
  readonly onClose: () => void;
  readonly onCreated: () => void;
}

type Channel = ExternalSaleRequest['salesChannel'];
type Delivery = ExternalSaleRequest['deliveryMethod'];

interface Line {
  productId: string;
  productName: string;
  variants: ProductVariantDto[];
  variantColor: string | null;
  variantSize: string | null;
  quantity: number;
  unitPrice: number;
}

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const CHANNELS: { value: Channel; label: string }[] = [
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'FACEBOOK', label: 'Facebook' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'MANUAL', label: 'Manual' },
];

const INPUT =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)]';

export default function RegisterSaleDrawer({ token, onClose, onCreated }: Props) {
  const idempotencyKey = useRef<string>(crypto.randomUUID());

  const [lines, setLines] = useState<Line[]>([]);
  const [buyerName, setBuyerName] = useState('');
  const [buyerContact, setBuyerContact] = useState('');
  const [channel, setChannel] = useState<Channel>('INSTAGRAM');
  const [paymentMethod, setPaymentMethod] = useState<'TRANSFER' | 'OTHER'>('TRANSFER');
  const [delivery, setDelivery] = useState<Delivery>('SHIPPING');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [term, setTerm] = useState('');
  const [results, setResults] = useState<ProductDto[]>([]);

  useEffect(() => {
    const q = term.trim();
    if (q.length < 2) {
      setResults([]);
      return;
    }
    let cancelled = false;
    const id = setTimeout(() => {
      void searchProducts({ q, active: true, inStock: true, page: 0, size: 8 }, 0, 8)
        .then((page) => {
          if (!cancelled) setResults(page.content);
        })
        .catch(() => {
          if (!cancelled) setResults([]);
        });
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(id);
    };
  }, [term]);

  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const setDialogRef = useCallback((node: HTMLDialogElement | null) => {
    dialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);

  const total = useMemo(
    () => lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0),
    [lines],
  );

  function addProduct(p: ProductDto) {
    const variants = p.variants ?? [];
    // Default to the first variant that actually has stock, so the operator does not have to
    // notice that the alphabetically-first size is the sold-out one.
    const preferred = variants.find((v) => v.stockAvailable > 0) ?? variants[0];
    setLines((current) => [
      ...current,
      {
        productId: p.id,
        productName: p.name,
        variants,
        variantColor: preferred ? preferred.color : null,
        variantSize: preferred ? preferred.size : null,
        quantity: 1,
        unitPrice: Math.round(p.price.amount),
      },
    ]);
    setTerm('');
    setResults([]);
  }

  function patchLine(index: number, patch: Partial<Line>) {
    setLines((current) => current.map((l, i) => (i === index ? { ...l, ...patch } : l)));
  }

  function removeLine(index: number) {
    setLines((current) => current.filter((_, i) => i !== index));
  }

  function validate(): string | null {
    if (lines.length === 0) return 'Agrega al menos un producto.';
    if (!buyerName.trim() || !buyerContact.trim()) return 'Nombre y contacto del comprador son obligatorios.';
    if (delivery === 'SHIPPING' && !address.trim()) return 'La dirección es obligatoria para un envío.';
    for (const l of lines) {
      if (l.variants.length > 0 && (!l.variantColor || !l.variantSize)) {
        return `Elige la variante de "${l.productName}".`;
      }
      if (l.quantity < 1) return `Cantidad inválida en "${l.productName}".`;
      if (l.unitPrice < 0 || !Number.isFinite(l.unitPrice)) return `Precio inválido en "${l.productName}".`;
    }
    return null;
  }

  async function submit() {
    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }
    setSubmitting(true);
    setError(null);
    const body: ExternalSaleRequest = {
      idempotencyKey: idempotencyKey.current,
      buyerName: buyerName.trim(),
      buyerContact: buyerContact.trim(),
      salesChannel: channel,
      paymentMethod,
      deliveryMethod: delivery,
      shippingAddress: delivery === 'SHIPPING' ? address.trim() : undefined,
      notes: notes.trim() || undefined,
      items: lines.map((l) => ({
        productId: l.productId,
        variantColor: l.variantColor,
        variantSize: l.variantSize,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
      })),
    };
    try {
      await registerExternalSale(body, token);
      onCreated();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo registrar la venta.');
      setSubmitting(false);
    }
  }

  const sizesFor = (line: Line, color: string) =>
    line.variants.filter((v) => v.color === color);

  const firstInStockSize = (line: Line, color: string) => {
    const inStock = sizesFor(line, color).find((v) => v.stockAvailable > 0);
    return (inStock ?? sizesFor(line, color)[0])?.size ?? null;
  };

  const colorsFor = (line: Line) => Array.from(new Set(line.variants.map((v) => v.color)));

  return (
    <Overlay>
      {/* A <dialog> is interactive; jsx-a11y misreads it. Escape is handled by onCancel; the
          backdrop click is a mouse-only convenience with that keyboard equivalent. Same pattern
          as every other admin drawer (SaleDetailDrawer, ReturnDetailDrawer, …). */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/click-events-have-key-events */}
      <dialog
        ref={setDialogRef}
        aria-label="Registrar venta"
        onCancel={(event) => {
          event.preventDefault();
          onClose();
        }}
        onClick={(event) => {
          if (event.target === dialogRef.current) onClose();
        }}
        className="fixed inset-y-0 right-0 m-0 h-full w-full max-w-[520px] p-0 border-0 flex flex-col bg-[var(--pe-surface-card)] shadow-2xl overflow-y-auto backdrop:bg-black/30 backdrop:backdrop-blur-[2px]"
      >
        <header className="flex items-start justify-between gap-4 px-5 py-4 border-b border-[var(--pe-border)] sticky top-0 bg-[var(--pe-surface-card)] z-10">
          <div>
            <h2 className="font-display text-2xl font-light leading-tight">Registrar venta</h2>
            <p className="text-[0.72rem] opacity-60 mt-0.5">
              Una venta hecha por redes o mostrador. Nace pagada y descuenta stock.
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Cerrar" className="p-1 hover:opacity-60">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 p-5 space-y-5">
          {error && (
            <output className="block text-[0.78rem] px-3 py-2 rounded-xs border border-pe-danger/40 text-pe-danger-ink">
              {error}
            </output>
          )}

          {/* Product search */}
          <div>
            <label className="relative block">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 opacity-40" aria-hidden="true" />
              <span className="sr-only">Buscar producto</span>
              <input
                type="search"
                value={term}
                onChange={(e) => setTerm(e.target.value)}
                placeholder="Buscar producto para agregar…"
                className={`${INPUT} pl-9`}
              />
            </label>
            {results.length > 0 && (
              <ul className="mt-1 border border-[var(--pe-border)] rounded-xs divide-y divide-[var(--pe-border)]">
                {results.map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      onClick={() => addProduct(p)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-[var(--pe-surface-soft)] flex items-center justify-between gap-3"
                    >
                      <span className="truncate">{p.name}</span>
                      <span className="tabular-nums opacity-60 shrink-0">{money.format(p.price.amount)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Lines */}
          {lines.length > 0 && (
            <ul className="space-y-3">
              {lines.map((line, index) => (
                <li key={`${line.productId}-${index}`} className="border border-[var(--pe-border)] rounded-xs p-3 space-y-2">
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-medium min-w-0 truncate">{line.productName}</p>
                    <button
                      type="button"
                      onClick={() => removeLine(index)}
                      aria-label={`Quitar ${line.productName}`}
                      className="p-1 opacity-60 hover:opacity-100 shrink-0"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>

                  {line.variants.length > 0 && (
                    <div className="flex gap-2">
                      <label className="flex-1">
                        <span className="sr-only">Color</span>
                        <select
                          value={line.variantColor ?? ''}
                          onChange={(e) => {
                            const color = e.target.value;
                            patchLine(index, { variantColor: color, variantSize: firstInStockSize(line, color) });
                          }}
                          className={INPUT}
                        >
                          {colorsFor(line).map((c) => (
                            <option key={c} value={c}>{c}</option>
                          ))}
                        </select>
                      </label>
                      <label className="flex-1">
                        <span className="sr-only">Talla</span>
                        <select
                          value={line.variantSize ?? ''}
                          onChange={(e) => patchLine(index, { variantSize: e.target.value })}
                          className={INPUT}
                        >
                          {sizesFor(line, line.variantColor ?? '').map((v) => (
                            <option key={v.size} value={v.size}>
                              {v.size}{v.stockAvailable <= 0 ? ' — sin stock' : ` (${v.stockAvailable})`}
                            </option>
                          ))}
                        </select>
                      </label>
                    </div>
                  )}

                  <div className="flex gap-2">
                    <label className="w-20">
                      <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Cant.</span>
                      <input
                        type="number"
                        min={1}
                        max={999}
                        value={line.quantity}
                        onChange={(e) => patchLine(index, { quantity: Math.max(1, Number(e.target.value) || 1) })}
                        className={INPUT}
                      />
                    </label>
                    <label className="flex-1">
                      <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Precio unitario (CLP)</span>
                      <input
                        type="number"
                        min={0}
                        step={100}
                        value={line.unitPrice}
                        onChange={(e) => patchLine(index, { unitPrice: Math.max(0, Math.round(Number(e.target.value) || 0)) })}
                        className={INPUT}
                      />
                    </label>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {/* Buyer */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label>
              <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Comprador</span>
              <input value={buyerName} onChange={(e) => setBuyerName(e.target.value)} className={INPUT} maxLength={160} />
            </label>
            <label>
              <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Contacto (fono / @usuario)</span>
              <input value={buyerContact} onChange={(e) => setBuyerContact(e.target.value)} className={INPUT} maxLength={160} />
            </label>
          </div>

          {/* Channel + payment */}
          <div className="space-y-2">
            <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Canal</span>
            <div className="flex flex-wrap gap-2">
              {CHANNELS.map((c) => (
                <button
                  key={c.value}
                  type="button"
                  aria-pressed={channel === c.value}
                  onClick={() => setChannel(c.value)}
                  className={`px-3 py-1.5 text-[0.72rem] rounded-xs border transition-colors ${
                    channel === c.value
                      ? 'border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)]'
                      : 'border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)]'
                  }`}
                >
                  {c.label}
                </button>
              ))}
            </div>
          </div>
          <label>
            <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Método de pago</span>
            <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as 'TRANSFER' | 'OTHER')} className={INPUT}>
              <option value="TRANSFER">Transferencia</option>
              <option value="OTHER">Otro</option>
            </select>
          </label>

          {/* Delivery */}
          <div className="space-y-2">
            <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Entrega</span>
            <div className="flex gap-2">
              <button
                type="button"
                aria-pressed={delivery === 'SHIPPING'}
                onClick={() => setDelivery('SHIPPING')}
                className={`flex-1 px-3 py-1.5 text-[0.72rem] rounded-xs border transition-colors ${
                  delivery === 'SHIPPING'
                    ? 'border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)]'
                    : 'border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)]'
                }`}
              >
                Envío
              </button>
              <button
                type="button"
                aria-pressed={delivery === 'PICKUP'}
                onClick={() => setDelivery('PICKUP')}
                className={`flex-1 px-3 py-1.5 text-[0.72rem] rounded-xs border transition-colors ${
                  delivery === 'PICKUP'
                    ? 'border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)]'
                    : 'border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)]'
                }`}
              >
                Retiro en persona
              </button>
            </div>
            {delivery === 'SHIPPING' && (
              <label className="block">
                <span className="sr-only">Dirección de envío</span>
                <textarea
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Dirección de envío (texto libre)"
                  rows={2}
                  maxLength={500}
                  className={INPUT}
                />
              </label>
            )}
          </div>

          <label className="block">
            <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Notas (opcional)</span>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={1000} className={INPUT} />
          </label>
        </div>

        <footer className="sticky bottom-0 bg-[var(--pe-surface-card)] border-t border-[var(--pe-border)] px-5 py-4 flex items-center justify-between gap-4">
          <div>
            <span className="text-[0.62rem] uppercase tracking-wider opacity-55">Total</span>
            <p className="text-lg font-medium tabular-nums">{money.format(total)}</p>
          </div>
          <button
            type="button"
            onClick={() => void submit()}
            disabled={submitting}
            className="px-5 py-2.5 text-[0.72rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)] disabled:opacity-50"
          >
            {submitting ? 'Registrando…' : 'Registrar venta'}
          </button>
        </footer>
      </dialog>
    </Overlay>
  );
}
