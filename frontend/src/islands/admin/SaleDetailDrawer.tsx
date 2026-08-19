import { useEffect, useState } from 'react';
import { X, FileText, Copy, Check, ExternalLink, Upload, Ban, RotateCcw } from 'lucide-react';
import {
  getOrderById,
  getPaymentByOrder,
  getSalesDocumentsByOrder,
  attachSalesDocumentFile,
  issueSalesDocument,
  voidSalesDocument,
  reissueSalesDocument,
  cancelSale,
  uploadSalesDocumentFile,
  fetchSalesDocumentFile,
  fetchPaymentProof,
  type OrderDto,
  type PaymentDto,
  type SaleSummaryDto,
  type SalesDocumentDto,
} from '../../lib/api';
import { orderStatusLabel } from '../../lib/orderStatusLabels';
import Overlay from './Overlay';

/** A delivered order cannot be cancelled, and a cancelled one is already done. */
const CANCELLABLE: string[] = [
  'PENDING_PAYMENT',
  'PAYMENT_UNDER_REVIEW',
  'PAID',
  'PREPARING_ORDER',
  'SHIPPED',
];

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const inputCls =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30 disabled:opacity-50';
const labelCls = 'text-[10px] tracking-widest uppercase opacity-60';

/**
 * Shows a document rather than handing over a link to it.
 *
 * A boleta is read, not downloaded: whoever is checking a sale wants to see the folio next to the
 * amounts on the same screen. Both kinds that the shop actually files are covered — a photograph
 * from the SII app and a PDF — because "open in a new tab" was losing the context that made the
 * document worth opening.
 *
 * The shop's own files arrive as an authenticated blob; a receipt that lives on somebody else's
 * host is pointed at directly, since there is no session of ours to send there.
 */
function DocumentViewer({
  source,
  label,
  onClose,
}: {
  source: { kind: 'blob'; blob: Blob } | { kind: 'external'; url: string };
  label: string;
  onClose: () => void;
}) {
  const [url, setUrl] = useState<string | null>(null);
  const [broken, setBroken] = useState(false);

  useEffect(() => {
    /*
     * Reset with the source, not only on mount. Opening a dead external receipt and then the
     * boleta showed the boleta as unreadable: the failure of the previous file was still being
     * reported about the next one, because only the URL had changed.
     */
    setBroken(false);
    if (source.kind === 'external') {
      setUrl(source.url);
      return;
    }
    const objectUrl = URL.createObjectURL(source.blob);
    setUrl(objectUrl);
    // Revoked when the source changes or the viewer closes, so files are not held for a session.
    return () => URL.revokeObjectURL(objectUrl);
  }, [source]);

  // Escape closes, like every other layer in this panel.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    };
    document.addEventListener('keydown', onKey, true);
    return () => document.removeEventListener('keydown', onKey, true);
  }, [onClose]);

  if (!url) return null;

  const isPdf =
    source.kind === 'blob'
      ? source.blob.type === 'application/pdf'
      : source.url.toLowerCase().split('?')[0].endsWith('.pdf');

  /*
   * Over everything, rather than inside the column. A boleta is checked at the size it was
   * photographed, and squeezed into half a drawer it was legible only in principle. It sits above
   * the drawer's own layer so the sale stays where it was: this covers the page, it does not
   * navigate away from it.
   */
  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-black/80 backdrop-blur-sm">
      <button
        type="button"
        aria-label="Cerrar la vista del documento"
        onClick={onClose}
        className="absolute inset-0 cursor-zoom-out"
      />
      <div className="relative z-[1] flex items-center justify-between gap-3 px-4 py-3 text-[var(--pe-on-dark)]">
        <span className="text-[11px] tracking-widest uppercase">{label}</span>
        <div className="flex items-center gap-4">
          <a
            href={url}
            target="_blank"
            rel="noreferrer noopener"
            className="text-[0.75rem] underline underline-offset-2 hover:opacity-70"
          >
            Abrir aparte
          </a>
          <button type="button" onClick={onClose} aria-label="Cerrar" className="p-1 hover:opacity-70">
            <X size={20} />
          </button>
        </div>
      </div>
      <div className="relative z-[1] flex-1 min-h-0 px-4 pb-4 flex items-center justify-center">
        {broken ? (
          /*
           * A receipt on somebody else's host can be gone, moved, or behind a login, and a broken
           * image icon tells whoever is checking the sale nothing about which of those happened.
           */
          <p className="max-w-md text-center text-[0.9rem] leading-relaxed text-[var(--pe-on-dark)]">
            No se pudo cargar el archivo desde {source.kind === 'external' ? new URL(url).host : 'la tienda'}.
            El enlace quedó guardado con el pago, pero el archivo no responde.
          </p>
        ) : isPdf ? (
          <iframe src={url} title={label} className="w-full h-full bg-white" />
        ) : (
          <img
            src={url}
            alt={label}
            onError={() => setBroken(true)}
            className="max-w-full max-h-full object-contain"
          />
        )}
      </div>
    </div>
  );
}

/**
 * Mirrors PaymentProofStorage.isStoredFile: an absolute URL was never a file the shop stored, so
 * asking our own endpoint for it can only fail. Kept to one line, and to the same rule, on purpose.
 */
function isExternalProof(reference: string): boolean {
  return reference.startsWith('http://') || reference.startsWith('https://');
}
const btnPrimary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs bg-[var(--pe-ink)] text-[var(--pe-surface)] hover:opacity-80 disabled:opacity-40 transition-opacity';
const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] disabled:opacity-40 transition-colors';
const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-red-300/60 text-red-500 hover:bg-red-50/50 disabled:opacity-40 transition-colors';

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
  /** Undoing a sale that took money is heavier than correcting a folio; only ADMIN holds it. */
  canCancelSale: boolean;
  onClose: () => void;
  onChanged: () => void;
}

export default function SaleDetailDrawer({
  sale,
  token,
  canIssue,
  canVoid,
  canCancelSale,
  onClose,
  onChanged,
}: Props) {
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
  /** Shown at the control itself: the drawer is long, and the banner at the top can be scrolled away. */
  const [fileError, setFileError] = useState<string | null>(null);
  const [attachError, setAttachError] = useState<string | null>(null);
  const [viewing, setViewing] = useState<
    { source: { kind: 'blob'; blob: Blob } | { kind: 'external'; url: string }; label: string } | null
  >(null);
  const [voidReason, setVoidReason] = useState('');
  const [mode, setMode] = useState<'idle' | 'void' | 'reissue'>('idle');
  /** Voiding a boleta and undoing the sale are different acts, so the second one is opt-in. */
  const [alsoCancelSale, setAlsoCancelSale] = useState(false);

  // The document the sale stands on. A credit note is ISSUED too and comes back newest-first, so
  // without excluding it this would hand the panel a note to void and reissue.
  const live = documents.find(
    (d) => d.status === 'ISSUED' && d.documentType !== 'NOTA_CREDITO',
  ) ?? null;
  const creditNotes = documents.filter(
    (d) => d.documentType === 'NOTA_CREDITO' && d.status !== 'VOIDED',
  );
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
    setFileError(null);
    try {
      const stored = await uploadSalesDocumentFile(file, token);
      setFileUrl(stored);
      setFileName(file.name);
    } catch (error) {
      /*
       * Losing this used to be silent from where the operator was looking: the boleta registered
       * without its image, the row said folio 153, and the file was never there to open.
       */
      setFileUrl(null);
      setFileName(null);
      setFileError(error instanceof Error ? error.message : 'No se pudo subir el archivo');
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
      setFileError(null);
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
    if (!voidReason.trim()) {
      setFeedback({ tone: 'error', text: 'Indica el motivo de la anulación.' });
      return;
    }
    if (!alsoCancelSale && !live) return;
    setBusy(true);
    setFeedback(null);
    try {
      if (alsoCancelSale) {
        // One call: the backend voids the document and cancels the order together, and it is the
        // cancellation that returns the units to the shelf.
        await cancelSale(sale.orderId, voidReason.trim(), token);
        setFeedback({
          tone: 'success',
          text: 'Venta anulada. La boleta quedó anulada y las unidades volvieron al stock.',
        });
      } else {
        await voidSalesDocument(live!.id, voidReason.trim(), token);
        setFeedback({
          tone: 'success',
          text: 'Boleta anulada. La venta sigue vigente y no podrá despacharse hasta emitir otra.',
        });
      }
      setVoidReason('');
      setAlsoCancelSale(false);
      setMode('idle');
      await reload();
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo anular' });
    } finally {
      setBusy(false);
    }
  }

  /** Filing the paper afterwards, which is how the boleta is actually made: by hand, then photographed. */
  async function attachToLive(documentId: string, file: File) {
    setBusy(true);
    setAttachError(null);
    try {
      await attachSalesDocumentFile(documentId, file, token);
      setFeedback({ tone: 'success', text: 'Archivo adjuntado a la boleta.' });
      await reload();
    } catch (error) {
      setAttachError(error instanceof Error ? error.message : 'No se pudo adjuntar el archivo');
    } finally {
      setBusy(false);
    }
  }

  async function openFile(documentId: string, folio: string) {
    setFeedback(null);
    try {
      const blob = await fetchSalesDocumentFile(documentId, token);
      setViewing({ source: { kind: 'blob', blob }, label: `Boleta ${folio}` });
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo abrir el archivo' });
    }
  }

  async function openProof(paymentId: string) {
    setFeedback(null);
    try {
      const blob = await fetchPaymentProof(paymentId, token);
      setViewing({ source: { kind: 'blob', blob }, label: 'Comprobante de transferencia' });
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo abrir el comprobante' });
    }
  }

  return (
    <Overlay>
      <div className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px]" onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={`Venta ${sale.publicReference ?? sale.orderId}`}
        className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-[560px] lg:max-w-[1000px] bg-[var(--pe-surface-card)] shadow-2xl overflow-y-auto"
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
              className={`text-[0.78rem] px-3 py-2 rounded-xs border ${
                feedback.tone === 'success'
                  ? 'border-green-300/60 text-green-700'
                  : 'border-red-300/60 text-red-600'
              }`}
            >
              {feedback.text}
            </p>
          )}

          {/*
            * Two columns from lg up: reading a sale and acting on it are different jobs, and the
            * single column made the second one live below the fold on every screen wide enough to
            * have shown it. Left is what the sale is; right is the paperwork and the viewer.
            */}
          <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
            <div className="space-y-4">
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

            </div>

            <div className="space-y-4">
          <Section label="Pago">
            <Row label="Método" value={sale.paymentMethod ?? '—'} />
            <Row label="Estado" value={sale.paymentStatus ?? '—'} />
            {payment?.proofReference && (
              isExternalProof(payment.proofReference) ? (
                /*
                 * A receipt that lives somewhere else is opened where it lives. Routing it through
                 * our authenticated endpoint answered 400 and the drawer showed a broken link on a
                 * sale that had a perfectly good proof: the endpoint serves the shop's own files,
                 * and an absolute URL was never one of them.
                 */
                <button
                  type="button"
                  onClick={() =>
                    setViewing({
                      source: { kind: 'external', url: payment.proofReference as string },
                      label: 'Comprobante (alojado fuera de la tienda)',
                    })
                  }
                  className="inline-flex items-center gap-1.5 text-[0.75rem] text-[var(--pe-ink)] underline underline-offset-2 hover:opacity-70"
                >
                  <ExternalLink size={12} /> Ver comprobante
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => void openProof(payment.id)}
                  className="inline-flex items-center gap-1.5 text-[0.75rem] text-[var(--pe-ink)] underline underline-offset-2 hover:opacity-70"
                >
                  <ExternalLink size={12} /> Ver comprobante
                </button>
              )
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
                  {live.fileAttached ? (
                    <button type="button" className={btnSecondary} onClick={() => void openFile(live.id, live.folio)}>
                      <FileText size={13} /> Ver archivo
                    </button>
                  ) : (
                    <div className="w-full space-y-1">
                      <label className={`${btnSecondary} cursor-pointer w-fit`}>
                        <Upload size={13} /> Adjuntar la imagen o el PDF
                        <input
                          type="file"
                          accept=".pdf,image/*"
                          className="hidden"
                          disabled={busy}
                          onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) void attachToLive(live.id, file);
                          }}
                        />
                      </label>
                      <p className="text-[0.7rem] opacity-60">
                        Quedó registrada sin archivo. Adjuntarlo no cambia el folio ni los montos.
                      </p>
                      {attachError && (
                        <p role="alert" className="text-[0.7rem] text-red-500">{attachError}</p>
                      )}
                    </div>
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

                {creditNotes.length > 0 && (
                  <div className="pt-3 border-t border-[var(--pe-border)] space-y-2">
                    <p className="text-[10px] tracking-widest uppercase opacity-60">
                      Notas de crédito
                    </p>
                    {creditNotes.map((note) => (
                      <div key={note.id} className="flex items-baseline justify-between gap-3 text-sm">
                        <span className="opacity-60">
                          {note.folio} · {note.referenceCode === 1 ? 'anula' : 'corrige monto'}
                        </span>
                        <span className="text-right tabular-nums">{money.format(note.totalAmount)}</span>
                      </div>
                    ))}
                    <p className="text-[0.68rem] opacity-50">
                      La boleta sigue válida: la nota la contrapesa. Se registra desde Devoluciones.
                    </p>
                  </div>
                )}
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-sm opacity-70">
                  Esta venta no tiene boleta registrada. No podrá despacharse hasta que se registre.
                </p>
                {/* The pending-queue case: paid, undeclared, and being undone. There is no document
                    to void, but the sale still has to give its units back. */}
                {canCancelSale && CANCELLABLE.includes(sale.orderStatus) && mode === 'idle' && (
                  <button
                    type="button"
                    className={btnDanger}
                    onClick={() => {
                      setAlsoCancelSale(true);
                      setMode('void');
                    }}
                  >
                    <Ban size={13} /> Anular la venta
                  </button>
                )}
              </div>
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
                  <>
                    {canCancelSale && (
                      <label className="flex items-start gap-2 pt-1">
                        <input
                          type="checkbox"
                          checked={alsoCancelSale}
                          onChange={(e) => setAlsoCancelSale(e.target.checked)}
                          className="h-4 w-4 mt-0.5 accent-[var(--pe-ink)]"
                        />
                        <span className="text-[0.78rem]">
                          Cerrar también la venta como anulada
                          <span className="block text-[0.7rem] opacity-60">
                            Devuelve las unidades al stock y libera el código de descuento. Sin esto,
                            solo se anula la boleta y la venta sigue vigente.
                          </span>
                        </span>
                      </label>
                    )}
                    <div className="flex gap-2">
                      <button type="button" className={btnDanger} disabled={busy} onClick={handleVoid}>
                        {alsoCancelSale ? 'Anular la venta completa' : 'Confirmar anulación'}
                      </button>
                      <button
                        type="button"
                        className={btnSecondary}
                        onClick={() => {
                          setMode('idle');
                          setAlsoCancelSale(false);
                        }}
                      >
                        Cancelar
                      </button>
                    </div>
                  </>
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
                  {fileError && (
                    <p role="alert" className="text-[0.7rem] text-red-500">
                      {fileError} La boleta se puede registrar igual, pero quedará sin imagen.
                    </p>
                  )}
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
          </div>
        </div>
      </aside>

      {viewing && (
        <DocumentViewer
          source={viewing.source}
          label={viewing.label}
          onClose={() => setViewing(null)}
        />
      )}
    </Overlay>
  );
}
