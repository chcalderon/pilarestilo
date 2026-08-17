import { useEffect, useState } from 'react';
import { X, FileText, Copy, Check, ExternalLink, Upload, Ban, RotateCcw } from 'lucide-react';
import {
  getOrderById,
  getPaymentByOrder,
  getSalesDocumentsByOrder,
  issueSalesDocument,
  voidSalesDocument,
  reissueSalesDocument,
  uploadSalesDocumentFile,
  fetchSalesDocumentFile,
  type OrderDto,
  type PaymentDto,
  type SaleSummaryDto,
  type SalesDocumentDto,
} from '../../lib/api';
import { orderStatusLabel } from '../../lib/orderStatusLabels';

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const inputCls =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-sm px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30 disabled:opacity-50';
const labelCls = 'text-[10px] tracking-widest uppercase opacity-60';
const btnPrimary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm bg-[var(--pe-ink)] text-[var(--pe-surface)] hover:opacity-80 disabled:opacity-40 transition-opacity';
const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] disabled:opacity-40 transition-colors';
const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm border border-red-300/60 text-red-500 hover:bg-red-50/50 disabled:opacity-40 transition-colors';

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <section className="rounded-md border border-[var(--pe-border)] bg-[var(--pe-surface-soft)] p-4 space-y-3">
      <p className={labelCls}>{label}</p>
      {children}
    </section>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <span className="opacity-60">{label}</span>
      <span className="text-right tabular-nums">{value}</span>
    </div>
  );
}

/** Copy-to-clipboard on the two fields the shop retypes most: the buyer email and the reference. */
function CopyField({ label, value }: { label: string; value: string | null | undefined }) {
  const [copied, setCopied] = useState(false);
  if (!value) return <Row label={label} value={<span className="opacity-40">Sin dato</span>} />;
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <span className="opacity-60">{label}</span>
      <button
        type="button"
        className="inline-flex items-center gap-1.5 text-right hover:opacity-70 transition-opacity"
        onClick={() => {
          navigator.clipboard?.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1600);
        }}
        aria-label={`Copiar ${label}`}
      >
        <span className="break-all">{value}</span>
        {copied ? <Check size={12} className="text-green-600 shrink-0" /> : <Copy size={12} className="opacity-40 shrink-0" />}
      </button>
    </div>
  );
}

interface Props {
  sale: SaleSummaryDto;
  token: string;
  canIssue: boolean;
  canVoid: boolean;
  onClose: () => void;
  onChanged: () => void;
}

export default function SaleDetailDrawer({ sale, token, canIssue, canVoid, onClose, onChanged }: Props) {
  const [order, setOrder] = useState<OrderDto | null>(null);
  const [payment, setPayment] = useState<PaymentDto | null>(null);
  const [documents, setDocuments] = useState<SalesDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const [folio, setFolio] = useState('');
  const [receiverRut, setReceiverRut] = useState('');
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [voidReason, setVoidReason] = useState('');
  const [mode, setMode] = useState<'idle' | 'void' | 'reissue'>('idle');

  const live = documents.find((d) => d.status === 'ISSUED') ?? null;
  const history = documents.filter((d) => d.status === 'VOIDED');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      getOrderById(sale.orderId, token).catch(() => null),
      getPaymentByOrder(sale.orderId, token),
      getSalesDocumentsByOrder(sale.orderId, token),
    ]).then(([o, p, docs]) => {
      if (cancelled) return;
      setOrder(o);
      setPayment(p);
      setDocuments(docs);
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [sale.orderId, token]);

  useEffect(() => {
    const onEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onEsc);
    return () => window.removeEventListener('keydown', onEsc);
  }, [onClose]);

  async function reload() {
    setDocuments(await getSalesDocumentsByOrder(sale.orderId, token));
    onChanged();
  }

  async function handleUpload(file: File) {
    setBusy(true);
    setFeedback(null);
    try {
      const stored = await uploadSalesDocumentFile(file, token);
      setFileUrl(stored);
      setFileName(file.name);
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo subir el archivo' });
    } finally {
      setBusy(false);
    }
  }

  async function handleIssue() {
    if (!folio.trim()) {
      setFeedback({ tone: 'error', text: 'El folio es obligatorio.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      if (mode === 'reissue' && live) {
        if (!voidReason.trim()) {
          setFeedback({ tone: 'error', text: 'Indica por qué se anula la boleta anterior.' });
          setBusy(false);
          return;
        }
        await reissueSalesDocument(live.id, {
          voidReason: voidReason.trim(),
          folio: folio.trim(),
          receiverRut: receiverRut.trim() || null,
          fileUrl,
        }, token);
        setFeedback({ tone: 'success', text: 'Boleta anulada y reemitida.' });
      } else {
        await issueSalesDocument({
          orderId: sale.orderId,
          folio: folio.trim(),
          receiverRut: receiverRut.trim() || null,
          fileUrl,
        }, token);
        setFeedback({ tone: 'success', text: 'Boleta registrada.' });
      }
      setFolio('');
      setReceiverRut('');
      setFileUrl(null);
      setFileName(null);
      setVoidReason('');
      setMode('idle');
      await reload();
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo registrar la boleta' });
    } finally {
      setBusy(false);
    }
  }

  async function handleVoid() {
    if (!live) return;
    if (!voidReason.trim()) {
      setFeedback({ tone: 'error', text: 'Indica el motivo de la anulación.' });
      return;
    }
    setBusy(true);
    setFeedback(null);
    try {
      await voidSalesDocument(live.id, voidReason.trim(), token);
      setVoidReason('');
      setMode('idle');
      setFeedback({
        tone: 'success',
        text: 'Boleta anulada. La venta queda sin documento y no podrá despacharse hasta emitir otra.',
      });
      await reload();
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo anular la boleta' });
    } finally {
      setBusy(false);
    }
  }

  async function openFile(documentId: string) {
    try {
      const blob = await fetchSalesDocumentFile(documentId, token);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo abrir el archivo' });
    }
  }

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px]" onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={`Venta ${sale.publicReference ?? sale.orderId}`}
        className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-[520px] bg-[var(--pe-surface-card)] shadow-2xl overflow-y-auto"
      >
        <header className="flex items-start justify-between gap-4 px-5 py-4 border-b border-[var(--pe-border)] sticky top-0 bg-[var(--pe-surface-card)] z-10">
          <div>
            <h2 className="font-display text-2xl font-light leading-tight">
              {sale.publicReference ?? 'Venta'}
            </h2>
            <p className="text-[0.72rem] opacity-60 mt-0.5">
              {orderStatusLabel(sale.orderStatus)} · {new Date(sale.createdAt).toLocaleString('es-CL')}
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Cerrar" className="p-1 hover:opacity-60">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 p-5 space-y-4">
          {feedback && (
            <p
              role="status"
              className={`text-[0.78rem] px-3 py-2 rounded-sm border ${
                feedback.tone === 'success'
                  ? 'border-green-300/60 text-green-700'
                  : 'border-red-300/60 text-red-600'
              }`}
            >
              {feedback.text}
            </p>
          )}

          <Section label="Cliente">
            <Row label="Nombre" value={sale.customerName ?? <span className="opacity-40">Sin nombre</span>} />
            <CopyField label="Correo" value={sale.customerEmail} />
            {order?.shippingAddressReference && (
              <Row label="Dirección" value={<span className="opacity-80">{order.shippingAddressReference}</span>} />
            )}
          </Section>

          <Section label="Productos">
            {loading && <p className="text-sm opacity-50">Cargando…</p>}
            {!loading && order && (
              <ul className="divide-y divide-[var(--pe-border)]">
                {order.items.map((item) => (
                  <li key={item.id} className="flex items-baseline justify-between gap-3 py-2 text-sm">
                    <span>
                      {item.productName}
                      <span className="opacity-50"> ×{item.quantity}</span>
                    </span>
                    <span className="tabular-nums">{money.format(item.unitPrice.amount)}</span>
                  </li>
                ))}
              </ul>
            )}
            {!loading && !order && <p className="text-sm opacity-50">No se pudo leer el detalle del pedido.</p>}
          </Section>

          <Section label="Montos">
            {order && (
              <>
                <Row label="Subtotal" value={money.format(order.subtotal.amount)} />
                {order.discountAmount.amount > 0 && (
                  <Row label="Descuento" value={`- ${money.format(order.discountAmount.amount)}`} />
                )}
                <Row label="Neto" value={order.netAmount ? money.format(order.netAmount.amount) : '—'} />
                <Row
                  label={`IVA${order.taxRate != null ? ` (${order.taxRate}%)` : ''}`}
                  value={order.taxAmount ? money.format(order.taxAmount.amount) : '—'}
                />
                <div className="pt-2 border-t border-[var(--pe-border)]">
                  <Row label="Total" value={<strong>{money.format(order.totalAmount.amount)}</strong>} />
                </div>
              </>
            )}
          </Section>

          <Section label="Pago">
            <Row label="Método" value={sale.paymentMethod ?? '—'} />
            <Row label="Estado" value={sale.paymentStatus ?? '—'} />
            {payment?.proofReference && (
              <a
                href={payment.proofReference}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-[0.75rem] text-[var(--pe-ink)] underline underline-offset-2 hover:opacity-70"
              >
                <ExternalLink size={12} /> Ver comprobante
              </a>
            )}
          </Section>

          <Section label="Boleta">
            {live ? (
              <div className="space-y-3">
                <Row label="Folio" value={<strong>{live.folio}</strong>} />
                <Row label="Emitida" value={new Date(live.issuedAt).toLocaleString('es-CL')} />
                <Row label="Neto" value={money.format(live.netAmount)} />
                <Row label={`IVA (${live.taxRate}%)`} value={money.format(live.taxAmount)} />
                <Row label="Total" value={money.format(live.totalAmount)} />
                {live.receiverRut && <Row label="RUT receptor" value={live.receiverRut} />}
                {/* Snapshot taken when the boleta was issued: it is who the document names, even if
                    the account has changed or been anonymised since. */}
                {live.receiverName && <Row label="A nombre de" value={live.receiverName} />}
                <CopyField label="Enviar a" value={live.receiverEmail} />
                <div className="flex flex-wrap gap-2 pt-1">
                  {live.fileAttached && (
                    <button type="button" className={btnSecondary} onClick={() => openFile(live.id)}>
                      <FileText size={13} /> Ver archivo
                    </button>
                  )}
                  {canVoid && mode === 'idle' && (
                    <button type="button" className={btnDanger} onClick={() => setMode('void')}>
                      <Ban size={13} /> Anular
                    </button>
                  )}
                  {canIssue && mode === 'idle' && (
                    <button type="button" className={btnSecondary} onClick={() => setMode('reissue')}>
                      <RotateCcw size={13} /> Anular y reemitir
                    </button>
                  )}
                </div>
              </div>
            ) : (
              <p className="text-sm opacity-70">
                Esta venta no tiene boleta registrada. No podrá despacharse hasta que se registre.
              </p>
            )}

            {(mode === 'void' || mode === 'reissue') && (
              <div className="space-y-2 pt-2 border-t border-[var(--pe-border)]">
                <label className="block space-y-1">
                  <span className={labelCls}>Motivo de la anulación</span>
                  <input
                    className={inputCls}
                    value={voidReason}
                    onChange={(e) => setVoidReason(e.target.value)}
                    placeholder="Folio equivocado, monto incorrecto…"
                  />
                </label>
                {mode === 'void' && (
                  <div className="flex gap-2">
                    <button type="button" className={btnDanger} disabled={busy} onClick={handleVoid}>
                      Confirmar anulación
                    </button>
                    <button type="button" className={btnSecondary} onClick={() => setMode('idle')}>
                      Cancelar
                    </button>
                  </div>
                )}
              </div>
            )}

            {canIssue && (!live || mode === 'reissue') && (
              <div className="space-y-3 pt-3 border-t border-[var(--pe-border)]">
                <label className="block space-y-1">
                  <span className={labelCls}>Folio *</span>
                  <input
                    className={inputCls}
                    value={folio}
                    onChange={(e) => setFolio(e.target.value)}
                    placeholder="1042"
                    inputMode="numeric"
                  />
                </label>
                <label className="block space-y-1">
                  <span className={labelCls}>RUT receptor (opcional)</span>
                  <input
                    className={inputCls}
                    value={receiverRut}
                    onChange={(e) => setReceiverRut(e.target.value)}
                    placeholder="12.345.678-9"
                  />
                  <span className="block text-[0.68rem] opacity-50">
                    La boleta no lo exige. Se guarda solo si lo escribes.
                  </span>
                </label>
                <div className="space-y-1">
                  <span className={labelCls}>Archivo (opcional)</span>
                  <label className={`${btnSecondary} cursor-pointer w-fit`}>
                    <Upload size={13} /> {fileName ? 'Cambiar archivo' : 'Subir PDF o imagen'}
                    <input
                      type="file"
                      accept=".pdf,image/*"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) handleUpload(file);
                      }}
                    />
                  </label>
                  {fileName && <p className="text-[0.7rem] opacity-60">{fileName}</p>}
                </div>
                <button type="button" className={btnPrimary} disabled={busy} onClick={handleIssue}>
                  {mode === 'reissue' ? 'Anular y registrar la nueva' : 'Registrar boleta'}
                </button>
              </div>
            )}
          </Section>

          {history.length > 0 && (
            <Section label="Documentos anulados">
              <ul className="divide-y divide-[var(--pe-border)]">
                {history.map((doc) => (
                  <li key={doc.id} className="py-2 text-[0.78rem]">
                    <div className="flex items-baseline justify-between gap-3">
                      <span>
                        Folio {doc.folio}
                        <span className="opacity-50"> · anulada</span>
                      </span>
                      <span className="opacity-50 tabular-nums">
                        {doc.voidedAt ? new Date(doc.voidedAt).toLocaleDateString('es-CL') : ''}
                      </span>
                    </div>
                    {doc.voidReason && <p className="opacity-60 mt-0.5">{doc.voidReason}</p>}
                  </li>
                ))}
              </ul>
            </Section>
          )}
        </div>
      </aside>
    </>
  );
}
