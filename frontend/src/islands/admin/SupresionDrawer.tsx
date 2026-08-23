import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, ShieldOff, X } from 'lucide-react';
import {
  anonymiseDeletionRequest,
  refuseDeletionRequest,
  type DeletionRequestDto,
} from '../../lib/api';
import Overlay from './Overlay';

interface Props {
  readonly request: DeletionRequestDto;
  readonly token: string;
  readonly canResolve: boolean;
  readonly onClose: () => void;
  readonly onResolved: (updated: DeletionRequestDto) => void;
}

const inputCls =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30 disabled:opacity-50';

const labelCls = 'text-[10px] tracking-widest uppercase opacity-60';

const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] disabled:opacity-40 transition-colors';

const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs bg-red-600 text-white hover:bg-red-700 disabled:opacity-40 transition-colors';

function Field({ label, children }: { readonly label: string; readonly children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className={labelCls}>{label}</p>
      <div className="text-sm break-words">{children}</div>
    </div>
  );
}

/**
 * Resolving a request from the desk. Two answers only, and they are not symmetrical: refusing is a
 * reply that can be revisited, anonymising cannot be taken back by anyone, which is why it asks
 * twice and spells out what goes before the button will work.
 */
export default function SupresionDrawer({ request, token, canResolve, onClose, onResolved }: Props) {
  const [mode, setMode] = useState<'view' | 'anonymise' | 'refuse'>('view');
  const [understood, setUnderstood] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  /*
   * Escape closes and focus lands inside the panel. Without the second half a keyboard user reads
   * the whole queue again on every open, because focus stays where the row was.
   */
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    panelRef.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const open = request.status === 'REQUESTED';

  async function run(action: () => Promise<DeletionRequestDto>) {
    setBusy(true);
    setError(null);
    try {
      onResolved(await action());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo resolver la solicitud');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Overlay>
      <button
        type="button"
        aria-label="Cerrar"
        onClick={onClose}
        className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px] cursor-default"
      />

      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Solicitud de supresión"
        className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-[460px] bg-[var(--pe-surface-card)] shadow-2xl overflow-y-auto outline-hidden"
      >
        <div className="flex items-start justify-between p-5 pb-4 border-b border-[var(--pe-border)] shrink-0">
          <div className="min-w-0">
            <p className="text-[10px] tracking-widest uppercase opacity-50">Solicitud de supresión</p>
            <h2 className="font-display text-xl font-light truncate">
              {request.customerName ?? 'Sin nombre'}
            </h2>
            <p className="text-[0.75rem] opacity-60 truncate">{request.customerEmail ?? '—'}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1 opacity-60 hover:opacity-100"
          >
            <X size={18} />
          </button>
        </div>

        <div className="p-5 space-y-5">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Pidió el">
              {new Date(request.requestedAt).toLocaleDateString('es-CL', {
                day: '2-digit',
                month: 'long',
                year: 'numeric',
              })}
            </Field>
            <Field label="Esperando">
              <span className="tabular-nums">{request.daysWaiting} días</span>
            </Field>
          </div>

          <Field label="Motivo que dio">
            {request.reason || <span className="opacity-40">No dio motivo</span>}
          </Field>

          {!open && (
            <div className="rounded-xs border border-[var(--pe-border)] bg-[var(--pe-surface-soft)] p-4 space-y-2">
              <p className="text-[0.78rem]">
                {request.status === 'ANONYMISED'
                  ? 'Ya fue anonimizada. La cuenta de arriba es lo que queda: nadie puede identificarla.'
                  : 'Fue rechazada.'}
              </p>
              {request.resolution && <p className="text-[0.78rem] opacity-70">{request.resolution}</p>}
              {request.resolvedAt && (
                <p className="text-[0.7rem] opacity-50 tabular-nums">
                  {new Date(request.resolvedAt).toLocaleString('es-CL')}
                </p>
              )}
            </div>
          )}

          {error && (
            <p
              role="alert"
              className="text-[0.78rem] px-3 py-2 rounded-xs border border-red-300/60 text-red-600"
            >
              {error}
            </p>
          )}

          {open && canResolve && mode === 'view' && (
            <div className="flex flex-wrap gap-2 pt-1">
              <button type="button" className={btnDanger} onClick={() => setMode('anonymise')}>
                <ShieldOff size={13} /> Anonimizar
              </button>
              <button type="button" className={btnSecondary} onClick={() => setMode('refuse')}>
                Rechazar con motivo
              </button>
            </div>
          )}

          {open && canResolve && mode === 'anonymise' && (
            <div className="space-y-3 rounded-xs border border-red-300/60 p-4">
              <p className="flex items-center gap-2 text-[0.8rem] font-medium text-red-600">
                <AlertTriangle size={14} aria-hidden="true" /> Esto no se puede deshacer
              </p>
              <ul className="text-[0.78rem] space-y-1.5 opacity-80 list-disc pl-4">
                <li>El nombre, el correo y el teléfono se reemplazan por datos anónimos.</li>
                <li>Las direcciones de despacho guardadas se borran.</li>
                <li>La cuenta queda desactivada: ya no podrá entrar.</li>
                <li>
                  Sus pedidos, pagos y boletas se conservan, pero apuntando a alguien que ya nadie
                  puede identificar. La ley tributaria obliga a guardar la boleta seis años.
                </li>
                <li>Los consentimientos quedan como prueba de lo que aceptó.</li>
              </ul>
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={understood}
                  onChange={(event) => setUnderstood(event.target.checked)}
                  className="mt-0.5 w-4 h-4 shrink-0"
                />
                <span className="text-[0.78rem]">
                  Entiendo que no hay vuelta atrás y que {request.customerEmail ?? 'esta cuenta'} deja
                  de existir como persona identificable.
                </span>
              </label>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className={btnDanger}
                  disabled={!understood || busy}
                  onClick={() => void run(() => anonymiseDeletionRequest(request.id, token))}
                >
                  {busy ? 'Anonimizando…' : 'Anonimizar definitivamente'}
                </button>
                <button
                  type="button"
                  className={btnSecondary}
                  disabled={busy}
                  onClick={() => {
                    setMode('view');
                    setUnderstood(false);
                  }}
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}

          {open && canResolve && mode === 'refuse' && (
            <div className="space-y-3 rounded-xs border border-[var(--pe-border)] p-4">
              <label className={labelCls} htmlFor="refuse-reason">
                Por qué se rechaza
              </label>
              <textarea
                id="refuse-reason"
                rows={3}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className={inputCls}
                placeholder="Ej: tiene un pedido en curso sin entregar"
              />
              <p className="text-[0.72rem] opacity-60">
                Se guarda con la solicitud: si la clienta reclama, esta es la respuesta que dio la
                tienda y la fecha en que la dio.
              </p>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className={btnSecondary}
                  disabled={reason.trim().length < 5 || busy}
                  onClick={() => void run(() => refuseDeletionRequest(request.id, reason.trim(), token))}
                >
                  {busy ? 'Guardando…' : 'Rechazar solicitud'}
                </button>
                <button
                  type="button"
                  className={btnSecondary}
                  disabled={busy}
                  onClick={() => setMode('view')}
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}

          {open && !canResolve && (
            <p className="text-[0.78rem] opacity-60">
              Puedes ver la solicitud, pero resolverla necesita el permiso privacy.resolve.
            </p>
          )}
        </div>
      </div>
    </Overlay>
  );
}
