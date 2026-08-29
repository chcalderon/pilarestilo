import { useCallback, useEffect, useRef, useState } from 'react';
import { X, Check, Ban, PackageCheck, Recycle, Trash2, Banknote, FileMinus, Upload } from 'lucide-react';
import {
  approveReturn,
  rejectReturn,
  receiveReturn,
  resolveDisposition,
  attachRefundAccount,
  registerRefund,
  getOrderById,
  getSalesDocumentsByOrder,
  issueCreditNote,
  uploadSalesDocumentFile,
  type OrderDto,
  type ReturnRequestDto,
  type SalesDocumentDto,
} from '../../lib/api';
import Overlay from './Overlay';

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const inputCls =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30 disabled:opacity-50';
const labelCls = 'text-[10px] tracking-widest uppercase opacity-60';
const btnPrimary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs bg-[var(--pe-ink)] text-[var(--pe-surface)] hover:opacity-80 disabled:opacity-40 transition-opacity';
const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] disabled:opacity-40 transition-colors';
const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-pe-danger/40 text-pe-danger-ink hover:bg-pe-danger-surface disabled:opacity-40 transition-colors';

function Section({ label, children }: { readonly label: string; readonly children: React.ReactNode }) {
  return (
    <section className="rounded-md border border-[var(--pe-border)] bg-[var(--pe-surface-soft)] p-4 space-y-3">
      <p className={labelCls}>{label}</p>
      {children}
    </section>
  );
}

function Row({ label, value }: { readonly label: string; readonly value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <span className="opacity-60">{label}</span>
      <span className="text-right tabular-nums">{value}</span>
    </div>
  );
}

type PrendaMode = 'idle' | 'reject' | 'discard';

interface SolicitudSectionProps {
  readonly request: ReturnRequestDto;
  readonly closed: boolean;
}

function SolicitudSection({ request, closed }: SolicitudSectionProps) {
  return (
    <Section label="Solicitud">
      <Row label="Motivo" value={request.reason ?? '—'} />
      <Row label="La abrió" value={request.requestedBy ? 'La clienta' : 'La tienda'} />
      <Row
        label="Plazo para reembolsar"
        value={
          closed ? (
            <span className="opacity-50">Cerrada</span>
          ) : (
            <span className={request.daysUntilDeadline <= 10 ? 'text-pe-warning-ink' : ''}>
              {request.daysUntilDeadline} días ·{' '}
              {new Date(request.deadlineAt).toLocaleDateString('es-CL')}
            </span>
          )
        }
      />
      {request.resolutionNote && <Row label="Resolución" value={request.resolutionNote} />}
    </Section>
  );
}

interface VentaSectionProps {
  readonly order: OrderDto;
}

function VentaSection({ order }: VentaSectionProps) {
  return (
    <Section label="Venta">
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
      <Row label="Total" value={<strong>{money.format(order.totalAmount.amount)}</strong>} />
    </Section>
  );
}

interface RejectFormProps {
  readonly rejectNote: string;
  readonly onRejectNoteChange: (value: string) => void;
  readonly busy: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}

function RejectForm({ rejectNote, onRejectNoteChange, busy, onConfirm, onCancel }: RejectFormProps) {
  return (
    <div className="space-y-2">
      <label className="block space-y-1">
        <span className={labelCls}>Motivo del rechazo</span>
        <input className={inputCls} value={rejectNote}
          onChange={(e) => onRejectNoteChange(e.target.value)}
          placeholder="Fuera de plazo, la prenda viene usada…" />
      </label>
      <div className="flex gap-2">
        <button type="button" className={btnDanger} disabled={busy} onClick={onConfirm}>
          Confirmar rechazo
        </button>
        <button type="button" className={btnSecondary} onClick={onCancel}>Cancelar</button>
      </div>
    </div>
  );
}

function DiscardForm({ discardNote, onDiscardNoteChange, busy, onConfirm, onCancel }: {
  readonly discardNote: string;
  readonly onDiscardNoteChange: (value: string) => void;
  readonly busy: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}) {
  return (
    <div className="space-y-2">
      <label className="block space-y-1">
        <span className={labelCls}>Por qué se descarta</span>
        <input className={inputCls} value={discardNote}
          onChange={(e) => onDiscardNoteChange(e.target.value)}
          placeholder="Mancha irrecuperable, rotura…" />
      </label>
      <div className="flex gap-2">
        <button type="button" className={btnDanger} disabled={busy} onClick={onConfirm}>
          Confirmar
        </button>
        <button type="button" className={btnSecondary} onClick={onCancel}>Cancelar</button>
      </div>
    </div>
  );
}

interface PrendaSectionProps {
  readonly request: ReturnRequestDto;
  readonly busy: boolean;
  readonly mode: PrendaMode;
  readonly onSetMode: (mode: PrendaMode) => void;
  readonly rejectNote: string;
  readonly onRejectNoteChange: (value: string) => void;
  readonly discardNote: string;
  readonly onDiscardNoteChange: (value: string) => void;
  readonly onApprove: () => void;
  readonly onReject: () => void;
  readonly onReceive: () => void;
  readonly onRestock: () => void;
  readonly onDiscard: () => void;
}

function PrendaSection({
  request, busy, mode, onSetMode, rejectNote, onRejectNoteChange, discardNote, onDiscardNoteChange,
  onApprove, onReject, onReceive, onRestock, onDiscard,
}: PrendaSectionProps) {
  return (
    <Section label="Prenda">
      {request.status === 'REQUESTED' && (
        <div className="flex flex-wrap gap-2">
          <button type="button" className={btnPrimary} disabled={busy} onClick={onApprove}>
            <Check size={13} /> Aprobar
          </button>
          {request.kind === 'DEVOLUCION' && mode === 'idle' && (
            <button type="button" className={btnDanger} onClick={() => onSetMode('reject')}>
              <Ban size={13} /> Rechazar
            </button>
          )}
        </div>
      )}

      {mode === 'reject' && (
        <RejectForm
          rejectNote={rejectNote}
          onRejectNoteChange={onRejectNoteChange}
          busy={busy}
          onConfirm={onReject}
          onCancel={() => onSetMode('idle')}
        />
      )}

      {request.status === 'APPROVED' && (
        <div className="space-y-2">
          <p className="text-[0.75rem] opacity-70">
            Al recibirla pasa a reacondicionamiento. <strong>No vuelve al stock todavía</strong>.
          </p>
          <button type="button" className={btnPrimary} disabled={busy} onClick={onReceive}>
            <PackageCheck size={13} /> Registrar que llegó
          </button>
        </div>
      )}

      {request.itemDisposition === 'PENDING_RECONDITIONING' && (
        <div className="space-y-2">
          <p className="text-[0.75rem] opacity-70">
            Terminado el reacondicionamiento, la prenda vuelve a la venta o se descarta.
          </p>
          <div className="flex flex-wrap gap-2">
            <button type="button" className={btnPrimary} disabled={busy} onClick={onRestock}>
              <Recycle size={13} /> Volver a la venta
            </button>
            {mode === 'idle' && (
              <button type="button" className={btnDanger} onClick={() => onSetMode('discard')}>
                <Trash2 size={13} /> Descartar
              </button>
            )}
          </div>
          {mode === 'discard' && (
            <DiscardForm
              discardNote={discardNote}
              onDiscardNoteChange={onDiscardNoteChange}
              busy={busy}
              onConfirm={onDiscard}
              onCancel={() => onSetMode('idle')}
            />
          )}
        </div>
      )}

      {request.itemDisposition === 'RESTOCKED' && <Row label="Prenda" value="De vuelta a la venta" />}
      {request.itemDisposition === 'DISCARDED' && (
        <Row label="Descartada" value={request.dispositionNote ?? '—'} />
      )}
    </Section>
  );
}

interface RefundAccount {
  readonly holder: string;
  readonly rut: string;
  readonly bankName: string;
  readonly accountType: string;
  readonly accountNumber: string;
}

interface RefundDraft {
  readonly amount: string;
  readonly method: string;
  readonly reference: string;
}

function RefundAccountForm({ account, onAccountChange, busy, onSave }: {
  readonly account: RefundAccount;
  readonly onAccountChange: (account: RefundAccount) => void;
  readonly busy: boolean;
  readonly onSave: () => void;
}) {
  return (
    <div className="space-y-2 pb-3 border-b border-[var(--pe-border)]">
      <p className={labelCls}>Cuenta de destino (solo transferencia)</p>
      <div className="grid grid-cols-2 gap-2">
        <input className={inputCls} placeholder="Titular" value={account.holder}
          onChange={(e) => onAccountChange({ ...account, holder: e.target.value })} />
        <input className={inputCls} placeholder="RUT" value={account.rut}
          onChange={(e) => onAccountChange({ ...account, rut: e.target.value })} />
        <input className={inputCls} placeholder="Banco" value={account.bankName}
          onChange={(e) => onAccountChange({ ...account, bankName: e.target.value })} />
        <input className={inputCls} placeholder="Tipo de cuenta" value={account.accountType}
          onChange={(e) => onAccountChange({ ...account, accountType: e.target.value })} />
        <input className={`${inputCls} col-span-2`} placeholder="N° de cuenta"
          value={account.accountNumber}
          onChange={(e) => onAccountChange({ ...account, accountNumber: e.target.value })} />
      </div>
      <p className="text-[0.68rem] opacity-50">
        Se guarda cifrada y se borra al cerrar el reembolso.
      </p>
      <button type="button" className={btnSecondary} disabled={busy} onClick={onSave}>
        Guardar cuenta
      </button>
    </div>
  );
}

function RefundForm({ request, account, onAccountChange, refund, onRefundChange, busy, onAttachAccount, onRegisterRefund }: {
  readonly request: ReturnRequestDto;
  readonly account: RefundAccount;
  readonly onAccountChange: (account: RefundAccount) => void;
  readonly refund: RefundDraft;
  readonly onRefundChange: (refund: RefundDraft) => void;
  readonly busy: boolean;
  readonly onAttachAccount: () => void;
  readonly onRegisterRefund: () => void;
}) {
  return (
    <div className="space-y-3">
      <p className="text-[0.75rem] opacity-70">
        El reembolso no espera a la prenda: la ley da 45 días para devolver el dinero.
      </p>

      {!request.refundAccountConfigured && (
        <RefundAccountForm account={account} onAccountChange={onAccountChange} busy={busy} onSave={onAttachAccount} />
      )}

      {request.refundAccountConfigured && (
        <Row
          label="Cuenta"
          value={`${request.refundBankName ?? ''} ····${request.refundAccountLast4 ?? ''}`}
        />
      )}

      <div className="grid grid-cols-2 gap-2">
        <label className="space-y-1">
          <span className={labelCls}>Monto</span>
          <input className={inputCls} inputMode="numeric" value={refund.amount}
            onChange={(e) => onRefundChange({ ...refund, amount: e.target.value })} />
        </label>
        <label className="space-y-1">
          <span className={labelCls}>Método</span>
          <select className={inputCls} value={refund.method}
            onChange={(e) => onRefundChange({ ...refund, method: e.target.value })}>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="TARJETA">Tarjeta</option>
            <option value="OTRO">Otro</option>
          </select>
        </label>
        <label className="space-y-1 col-span-2">
          <span className={labelCls}>Referencia de la operación</span>
          <input className={inputCls} value={refund.reference}
            onChange={(e) => onRefundChange({ ...refund, reference: e.target.value })}
            placeholder="N° de transferencia" />
        </label>
      </div>
      <button type="button" className={btnPrimary} disabled={busy} onClick={onRegisterRefund}>
        <Banknote size={13} /> Registrar reembolso
      </button>
    </div>
  );
}

interface DineroSectionProps {
  readonly request: ReturnRequestDto;
  readonly closed: boolean;
  readonly canRefund: boolean;
  readonly busy: boolean;
  readonly account: RefundAccount;
  readonly onAccountChange: (account: RefundAccount) => void;
  readonly refund: RefundDraft;
  readonly onRefundChange: (refund: RefundDraft) => void;
  readonly onAttachAccount: () => void;
  readonly onRegisterRefund: () => void;
}

function refundedContent(request: ReturnRequestDto) {
  return (
    <>
      <Row label="Monto" value={money.format(request.refundAmount ?? 0)} />
      <Row label="Método" value={request.refundMethod ?? '—'} />
      <Row label="Referencia" value={request.refundReference ?? '—'} />
      {request.refundBankName && (
        <Row
          label="Cuenta"
          value={`${request.refundBankName} ····${request.refundAccountLast4 ?? ''}`}
        />
      )}
      <p className="text-[0.68rem] opacity-50">
        El número de cuenta se borró al cerrar el reembolso. Queda la referencia de la operación.
      </p>
    </>
  );
}

function DineroSection({
  request, closed, canRefund, busy, account, onAccountChange, refund, onRefundChange, onAttachAccount, onRegisterRefund,
}: DineroSectionProps) {
  let content: React.ReactNode;
  if (request.status === 'REFUNDED') {
    content = refundedContent(request);
  } else if (canRefund && !closed) {
    content = (
      <RefundForm
        request={request}
        account={account}
        onAccountChange={onAccountChange}
        refund={refund}
        onRefundChange={onRefundChange}
        busy={busy}
        onAttachAccount={onAttachAccount}
        onRegisterRefund={onRegisterRefund}
      />
    );
  } else {
    content = (
      <p className="text-sm opacity-60">
        {closed ? 'Devolución cerrada sin reembolso.' : 'No tienes permiso para registrar reembolsos.'}
      </p>
    );
  }

  return (
    <Section label="Dinero">
      {content}
    </Section>
  );
}

function creditNoteReferenceLabel(issuedNote: SalesDocumentDto, liveSale: SalesDocumentDto | null): string {
  const documentLabel = liveSale ? liveSale.documentType.toLowerCase() : 'boleta';
  const action = issuedNote.referenceCode === 1 ? 'Anula la' : 'Corrige el monto de la';
  return `${action} ${documentLabel} ${liveSale?.folio ?? ''}`;
}

function IssuedCreditNote({ issuedNote, liveSale }: { readonly issuedNote: SalesDocumentDto; readonly liveSale: SalesDocumentDto | null }) {
  return (
    <>
      <Row label="Folio" value={issuedNote.folio} />
      <Row label="Referencia" value={creditNoteReferenceLabel(issuedNote, liveSale)} />
      <Row label="Monto" value={money.format(issuedNote.totalAmount)} />
      <Row label="Neto / IVA" value={`${money.format(issuedNote.netAmount)} · ${money.format(issuedNote.taxAmount)}`} />
    </>
  );
}

interface CreditNoteDraft {
  readonly folio: string;
  readonly amount: string;
  readonly fileUrl: string | null;
}

function uploadButtonLabel(uploading: boolean, hasFile: boolean): string {
  if (uploading) return 'Subiendo…';
  return hasFile ? 'Archivo listo' : 'Adjuntar archivo';
}

function creditNoteAmountHint(creditNote: CreditNoteDraft, liveSale: SalesDocumentDto): string {
  const documentLabel = liveSale.documentType.toLowerCase();
  return Number(creditNote.amount) >= liveSale.totalAmount
    ? `Anula la ${documentLabel} completa (referencia 1).`
    : `Corrige el monto de la ${documentLabel} (referencia 3).`;
}

function CreditNoteForm({
  liveSale, staleForCreditNote, creditNote, onCreditNoteChange, uploading, busy, onAttachFile, onRegister,
}: {
  readonly liveSale: SalesDocumentDto;
  readonly staleForCreditNote: boolean;
  readonly creditNote: CreditNoteDraft;
  readonly onCreditNoteChange: (creditNote: CreditNoteDraft) => void;
  readonly uploading: boolean;
  readonly busy: boolean;
  readonly onAttachFile: (file: File) => void;
  readonly onRegister: () => void;
}) {
  return (
    <div className="space-y-3">
      <p className="text-[0.75rem] opacity-70">
        La boleta {liveSale.folio} ya fue declarada al SII, así que no se anula: se
        contrapesa con una nota de crédito. Emítela en eBoleta o en el sitio del SII y
        registra aquí el folio que te dieron.
      </p>
      {staleForCreditNote && (
        <p className="text-[0.72rem] text-pe-warning-ink">
          La boleta tiene más de seis meses. Pasado ese plazo el SII ya no permite rebajar
          el débito fiscal; regístrala igual si la necesitas para tu contabilidad.
        </p>
      )}
      <div className="grid grid-cols-2 gap-2">
        <label className="space-y-1">
          <span className={labelCls}>Folio</span>
          <input className={inputCls} value={creditNote.folio}
            onChange={(e) => onCreditNoteChange({ ...creditNote, folio: e.target.value })} />
        </label>
        <label className="space-y-1">
          <span className={labelCls}>Monto acreditado</span>
          <input className={inputCls} inputMode="numeric" value={creditNote.amount}
            onChange={(e) => onCreditNoteChange({ ...creditNote, amount: e.target.value })} />
        </label>
      </div>
      <p className="text-[0.68rem] opacity-50">{creditNoteAmountHint(creditNote, liveSale)}</p>
      <label className={`${btnSecondary} cursor-pointer`}>
        <Upload size={13} />
        {uploadButtonLabel(uploading, Boolean(creditNote.fileUrl))}
        <input type="file" className="hidden" accept="application/pdf,image/*"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) onAttachFile(file);
          }} />
      </label>
      <div>
        <button type="button" className={btnPrimary} disabled={busy || !creditNote.folio} onClick={onRegister}>
          <FileMinus size={13} /> Registrar nota de crédito
        </button>
      </div>
    </div>
  );
}

interface NotaCreditoSectionProps {
  readonly request: ReturnRequestDto;
  readonly canRefund: boolean;
  readonly issuedNote: SalesDocumentDto | null;
  readonly liveSale: SalesDocumentDto | null;
  readonly staleForCreditNote: boolean;
  readonly creditNote: CreditNoteDraft;
  readonly onCreditNoteChange: (creditNote: CreditNoteDraft) => void;
  readonly uploading: boolean;
  readonly busy: boolean;
  readonly onAttachFile: (file: File) => void;
  readonly onRegister: () => void;
}

function notaCreditoBody({
  request, canRefund, issuedNote, liveSale, staleForCreditNote, creditNote, onCreditNoteChange, uploading, busy, onAttachFile, onRegister,
}: NotaCreditoSectionProps) {
  if (issuedNote) return <IssuedCreditNote issuedNote={issuedNote} liveSale={liveSale} />;
  if (request.status !== 'REFUNDED') {
    return <p className="text-sm opacity-60">Se registra una vez devuelto el dinero.</p>;
  }
  if (!liveSale) {
    return <p className="text-sm opacity-60">Esta venta no tiene boleta viva, así que no hay documento que anular.</p>;
  }
  if (!canRefund) {
    return <p className="text-sm opacity-60">No tienes permiso para registrar documentos.</p>;
  }
  return (
    <CreditNoteForm
      liveSale={liveSale}
      staleForCreditNote={staleForCreditNote}
      creditNote={creditNote}
      onCreditNoteChange={onCreditNoteChange}
      uploading={uploading}
      busy={busy}
      onAttachFile={onAttachFile}
      onRegister={onRegister}
    />
  );
}

function NotaCreditoSection(props: NotaCreditoSectionProps) {
  return <Section label="Nota de crédito">{notaCreditoBody(props)}</Section>;
}

interface Props {
  readonly request: ReturnRequestDto;
  readonly token: string;
  readonly canManage: boolean;
  readonly canRefund: boolean;
  readonly onClose: () => void;
  readonly onChanged: (updated: ReturnRequestDto) => void;
}

export default function ReturnDetailDrawer({
  request,
  token,
  canManage,
  canRefund,
  onClose,
  onChanged,
}: Props) {
  const [order, setOrder] = useState<OrderDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  const [rejectNote, setRejectNote] = useState('');
  const [discardNote, setDiscardNote] = useState('');
  const [mode, setMode] = useState<PrendaMode>('idle');

  const [account, setAccount] = useState({
    holder: '', rut: '', bankName: '', accountType: 'Cuenta Corriente', accountNumber: '',
  });
  const [refund, setRefund] = useState({ amount: '', method: 'TRANSFERENCIA', reference: '' });

  const [documents, setDocuments] = useState<SalesDocumentDto[]>([]);
  const [creditNote, setCreditNote] = useState({ folio: '', amount: '', fileUrl: '' as string | null });
  const [uploading, setUploading] = useState(false);

  const closed = request.status === 'REFUNDED' || request.status === 'REJECTED';
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  /*
   * A ref callback, not a mount-effect: <Overlay> renders null on its first pass and only
   * portals its children in once its own effect resolves the host element, so the dialog node
   * does not exist yet when a plain `useEffect(() => ref.current?.showModal(), [])` would fire.
   * A callback ref runs the instant React actually attaches the node, whichever render that is.
   */
  const setDialogRef = useCallback((node: HTMLDialogElement | null) => {
    dialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);

  useEffect(() => {
    getOrderById(request.orderId, token).then(setOrder).catch(() => setOrder(null));
  }, [request.orderId, token]);

  useEffect(() => {
    getSalesDocumentsByOrder(request.orderId, token).then(setDocuments).catch(() => setDocuments([]));
  }, [request.orderId, token, request.status]);

  useEffect(() => {
    if (order && !refund.amount) {
      setRefund((current) => ({ ...current, amount: String(order.totalAmount.amount) }));
    }
  }, [order]);

  useEffect(() => {
    if (request.refundAmount && !creditNote.amount) {
      setCreditNote((current) => ({ ...current, amount: String(request.refundAmount) }));
    }
  }, [request.refundAmount]);

  /** The document the sale still stands on; a credit note acts upon this one. */
  const liveSale = documents.find(
    (document) => document.status !== 'VOIDED' && document.documentType !== 'NOTA_CREDITO',
  ) ?? null;
  const issuedNote = documents.find(
    (document) => document.documentType === 'NOTA_CREDITO' && document.status !== 'VOIDED',
  ) ?? null;
  /**
   * The SII does not allow the débito fiscal to be reduced more than six months after the document.
   * A warning rather than a block: the shop may still need the note for its own books.
   */
  const staleForCreditNote = liveSale
    ? Date.now() - new Date(liveSale.issuedAt).getTime() > 183 * 86_400_000
    : false;

  async function attachFile(file: File) {
    setUploading(true);
    setFeedback(null);
    try {
      const stored = await uploadSalesDocumentFile(file, token);
      setCreditNote((current) => ({ ...current, fileUrl: stored }));
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo subir el archivo' });
    } finally {
      setUploading(false);
    }
  }

  async function registerCreditNote() {
    setBusy(true);
    setFeedback(null);
    try {
      const created = await issueCreditNote({
        orderId: request.orderId,
        folio: creditNote.folio.trim(),
        amount: Number(creditNote.amount),
        fileUrl: creditNote.fileUrl,
        returnId: request.id,
      }, token);
      setDocuments((current) => [created, ...current]);
      setFeedback({ tone: 'success', text: `Nota de crédito ${created.folio} registrada.` });
      onChanged({ ...request, creditNoteId: created.id });
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo registrar' });
    } finally {
      setBusy(false);
    }
  }

  async function run(action: () => Promise<ReturnRequestDto>, success: string) {
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await action();
      setFeedback({ tone: 'success', text: success });
      setMode('idle');
      onChanged(updated);
    } catch (error) {
      setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'No se pudo completar' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Overlay>
      <dialog
        ref={setDialogRef}
        aria-label="Detalle de la devolución"
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
            <h2 className="font-display text-2xl font-light leading-tight">
              {request.kind === 'RETRACTO' ? 'Retracto' : 'Devolución'}
            </h2>
            <p className="text-[0.72rem] opacity-60 mt-0.5">
              {order?.publicReference ?? request.orderId.slice(0, 8)} ·{' '}
              {new Date(request.requestedAt).toLocaleString('es-CL')}
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Cerrar" className="p-1 hover:opacity-60">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 p-5 space-y-4">
          {feedback && (
            <output
              className={`block text-[0.78rem] px-3 py-2 rounded-xs border ${
                feedback.tone === 'success' ? 'border-pe-positive/40 text-pe-positive-ink' : 'border-pe-danger/40 text-pe-danger-ink'
              }`}
            >
              {feedback.text}
            </output>
          )}

          {request.kind === 'RETRACTO' && request.status === 'REQUESTED' && (
            <p className="text-[0.78rem] px-3 py-2 rounded-xs border border-[var(--pe-border)] bg-[var(--pe-surface-soft)]">
              La clienta ejerció su derecho a retracto dentro de plazo. <strong>No se puede rechazar</strong>:
              la ley lo trata como un derecho, no como una solicitud.
            </p>
          )}

          <SolicitudSection request={request} closed={closed} />

          {order && <VentaSection order={order} />}

          {canManage && !closed && (
            <PrendaSection
              request={request}
              busy={busy}
              mode={mode}
              onSetMode={setMode}
              rejectNote={rejectNote}
              onRejectNoteChange={setRejectNote}
              discardNote={discardNote}
              onDiscardNoteChange={setDiscardNote}
              onApprove={() => run(() => approveReturn(request.id, token), 'Devolución aprobada.')}
              onReject={() => run(() => rejectReturn(request.id, rejectNote, token), 'Devolución rechazada.')}
              onReceive={() => run(() => receiveReturn(request.id, token), 'Prenda recibida, en reacondicionamiento.')}
              onRestock={() => run(
                () => resolveDisposition(request.id, 'RESTOCKED', null, token),
                'De vuelta a la venta: el stock subió.')}
              onDiscard={() => run(
                () => resolveDisposition(request.id, 'DISCARDED', discardNote, token),
                'Prenda descartada. El stock no cambió.')}
            />
          )}

          <DineroSection
            request={request}
            closed={closed}
            canRefund={canRefund}
            busy={busy}
            account={account}
            onAccountChange={setAccount}
            refund={refund}
            onRefundChange={setRefund}
            onAttachAccount={() => run(() => attachRefundAccount(request.id, account, token), 'Cuenta guardada.')}
            onRegisterRefund={() => run(
              () => registerRefund(request.id, {
                amount: Number(refund.amount),
                currency: 'CLP',
                method: refund.method,
                reference: refund.reference,
              }, token),
              'Reembolso registrado. La devolución queda cerrada.')}
          />

          <NotaCreditoSection
            request={request}
            canRefund={canRefund}
            issuedNote={issuedNote}
            liveSale={liveSale}
            staleForCreditNote={staleForCreditNote}
            creditNote={creditNote}
            onCreditNoteChange={setCreditNote}
            uploading={uploading}
            busy={busy}
            onAttachFile={(file) => void attachFile(file)}
            onRegister={() => void registerCreditNote()}
          />
        </div>
      </dialog>
    </Overlay>
  );
}
