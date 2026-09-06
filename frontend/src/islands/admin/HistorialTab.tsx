import { useEffect, useState } from 'react';
import { Ban, CheckCircle2, ChevronRight, Clock, ExternalLink, Loader2, RefreshCw } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  getPublicationBatches,
  getPublicationBatchDetail,
  retryBatchFailed,
  retryPublication,
  cancelBatch,
  rescheduleBatch,
  type PublicationBatchSummary,
  type PublicationBatchDetail,
} from '../../lib/api';
import { instantToSantiagoLabel, instantToSantiagoInputValue, santiagoWallTimeToInstant } from '../../lib/santiagoTime';

type Preload = {
  productIds: string[];
  captionTemplate: string;
  hashtags: string[];
  campaignLabel: string | null;
  scheduledAt?: string | null;
  imageSelections?: Record<string, string[]>;
};
type Props = {
  onRepublish: (p: Preload) => void;
  onGoToPublish: () => void;
  onEditScheduled: (
    batchId: string,
    p: Required<Pick<Preload, 'productIds' | 'captionTemplate' | 'hashtags' | 'campaignLabel' | 'scheduledAt'>>
      & { imageSelections?: Record<string, string[]> },
  ) => void;
};

const PLATFORM_SHORT: Record<string, string> = { INSTAGRAM: 'IG', FACEBOOK: 'FB' };
const PLATFORM_NAME: Record<string, string> = { INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook' };

function relativeTime(iso: string): string {
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return 'recién';
  if (mins < 60) return `hace ${mins} min`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `hace ${hrs} h`;
  return `hace ${Math.round(hrs / 24)} d`;
}

function StatusPill({ status }: { status: string }) {
  if (status === 'PUBLISHED') {
    return (
      <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-positive-surface text-pe-positive-ink">
        <CheckCircle2 size={12} /> Publicado
      </span>
    );
  }
  if (status === 'FAILED') {
    return (
      <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-danger-surface text-pe-danger-ink">
        <span aria-hidden="true">✗</span> Falló
      </span>
    );
  }
  if (status === 'SCHEDULED') {
    return (
      <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-warning-surface text-pe-warning-ink">
        <Clock size={12} /> Programado
      </span>
    );
  }
  if (status === 'CANCELLED') {
    return (
      <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-surface text-pe-muted">
        <Ban size={12} /> Cancelado
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 text-pe-muted">
      <Loader2 size={12} /> {status === 'PUBLISHING' ? 'Publicando' : status}
    </span>
  );
}

export default function HistorialTab({ onRepublish, onGoToPublish, onEditScheduled }: Props) {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [batches, setBatches] = useState<PublicationBatchSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [details, setDetails] = useState<Map<string, PublicationBatchDetail>>(new Map());
  const [busyBatch, setBusyBatch] = useState<string | null>(null);
  const [reschedulingFor, setReschedulingFor] = useState<string | null>(null);
  const [rescheduleInput, setRescheduleInput] = useState('');
  const [openError, setOpenError] = useState<Set<string>>(new Set());

  function load() {
    setLoadError(null);
    setBatches(null);
    getPublicationBatches(effectiveToken)
      .then(setBatches)
      .catch(() => setLoadError('No se pudo cargar el historial.'));
  }
  useEffect(load, [effectiveToken]);

  async function toggle(batchId: string | null) {
    if (batchId === null) return;
    if (expanded === batchId) {
      setExpanded(null);
      return;
    }
    setExpanded(batchId);
    if (!details.has(batchId)) {
      try {
        const d = await getPublicationBatchDetail(batchId, effectiveToken);
        setDetails((prev) => new Map(prev).set(batchId, d));
      } catch {
        /* leave it unexpanded-with-no-detail; the spinner text stays */
      }
    }
  }

  async function doRetryBatch(batchId: string) {
    setBusyBatch(batchId);
    try {
      const d = await retryBatchFailed(batchId, effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
      load();
    } finally {
      setBusyBatch(null);
    }
  }

  async function doRetryRow(batchId: string, publicationId: string) {
    setBusyBatch(batchId);
    try {
      await retryPublication(publicationId, effectiveToken);
      const d = await getPublicationBatchDetail(batchId, effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
    } finally {
      setBusyBatch(null);
    }
  }

  async function doCancel(batchId: string) {
    setBusyBatch(batchId);
    try {
      const d = await cancelBatch(batchId, effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
      load();
    } finally {
      setBusyBatch(null);
    }
  }

  async function doReschedule(batchId: string) {
    if (!rescheduleInput) return;
    setBusyBatch(batchId);
    try {
      const d = await rescheduleBatch(batchId, santiagoWallTimeToInstant(rescheduleInput), effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
      setReschedulingFor(null);
      load();
    } finally {
      setBusyBatch(null);
    }
  }

  if (loadError) {
    return (
      <div className="text-sm text-pe-danger-ink">
        {loadError}{' '}
        <button type="button" onClick={load} className="underline">
          Reintentar
        </button>
      </div>
    );
  }
  if (batches === null) {
    return (
      <ul className="flex flex-col gap-2" aria-busy="true">
        {[0, 1, 2].map((i) => (
          <li key={i} className="h-14 bg-pe-surface border border-pe-border animate-pulse" />
        ))}
      </ul>
    );
  }
  if (batches.length === 0) {
    return (
      <div className="text-sm text-pe-muted flex flex-col items-start gap-2">
        <p>Todavia no publicaste ninguna tanda.</p>
        <button type="button" onClick={onGoToPublish} className="bg-pe-rose text-pe-white px-3 py-1.5 rounded-xs text-sm">
          Ir a Publicar
        </button>
      </div>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {batches.map((b) => {
        const key = b.batchId ?? '__none__';
        const isOpen = expanded === b.batchId;
        const detail = b.batchId ? details.get(b.batchId) : undefined;
        return (
          <li key={key} className="border border-pe-border">
            <button
              type="button"
              aria-expanded={b.batchId ? isOpen : undefined}
              disabled={b.batchId === null}
              onClick={() => toggle(b.batchId)}
              className="w-full flex items-center gap-3 p-3 text-left disabled:cursor-default"
            >
              {b.batchId !== null && (
                <ChevronRight
                  size={16}
                  className={[
                    'text-pe-muted shrink-0 transition-transform motion-reduce:transition-none',
                    isOpen ? 'rotate-90' : '',
                  ].join(' ')}
                />
              )}
              <div className="flex-1 min-w-0">
                <p className={b.campaignLabel ? 'text-sm font-medium truncate' : 'text-sm text-pe-muted'}>
                  {b.campaignLabel ?? 'Sin campaña'}
                </p>
                <p className="text-[0.72rem] text-pe-muted" title={new Date(b.createdAt).toLocaleString('es-CL')}>
                  {relativeTime(b.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                {b.platforms.map((p) => (
                  <span key={p} className="text-[0.66rem] px-1.5 py-0.5 bg-pe-surface border border-pe-border text-pe-muted">
                    {PLATFORM_SHORT[p] ?? p}
                  </span>
                ))}
              </div>
              {b.scheduledAt != null && b.scheduled > 0 ? (
                <p className="text-[0.78rem] shrink-0 text-pe-warning-ink">
                  ◷ Programada para {instantToSantiagoLabel(b.scheduledAt)}
                  {b.failed > 0 && <span className="text-pe-danger-ink"> · {b.failed} fallidos</span>}
                </p>
              ) : (
                <p className="text-[0.78rem] shrink-0 tabular-nums">
                  <span className="text-pe-positive-ink">{b.published} publicados</span>
                  {b.failed > 0 && <span className="text-pe-danger-ink"> · {b.failed} fallidos</span>}
                  {b.scheduled > 0 && <span className="text-pe-warning-ink"> · {b.scheduled} programados</span>}
                </p>
              )}
            </button>

            {isOpen && b.batchId && (
              <div className="border-t border-pe-border p-3 flex flex-col gap-3">
                {detail && detail.rows.some((r) => r.status === 'SCHEDULED') ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={() => doCancel(b.batchId as string)}
                      disabled={busyBatch === b.batchId}
                      className="inline-flex items-center gap-1.5 text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose disabled:opacity-50"
                    >
                      <Ban size={12} /> Cancelar programación
                    </button>
                    {reschedulingFor === b.batchId ? (
                      <span className="inline-flex items-center gap-1.5">
                        <input
                          type="datetime-local"
                          value={rescheduleInput}
                          onChange={(e) => setRescheduleInput(e.target.value)}
                          className="bg-pe-surface border border-pe-border rounded-xs px-2 py-1 text-xs text-pe-black"
                        />
                        <button
                          type="button"
                          onClick={() => doReschedule(b.batchId as string)}
                          disabled={busyBatch === b.batchId}
                          className="text-[0.78rem] bg-pe-rose text-pe-white px-2.5 py-1 rounded-xs disabled:opacity-50"
                        >
                          Guardar
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setReschedulingFor(b.batchId as string);
                          setRescheduleInput(detail.scheduledAt ? instantToSantiagoInputValue(detail.scheduledAt) : '');
                        }}
                        className="text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose"
                      >
                        Cambiar hora
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() =>
                        onEditScheduled(b.batchId as string, {
                          productIds: detail.productIds,
                          captionTemplate: detail.captionTemplate ?? '',
                          hashtags: detail.hashtags,
                          campaignLabel: detail.campaignLabel,
                          scheduledAt: detail.scheduledAt,
                          imageSelections: Object.fromEntries(
                            detail.productIds
                              .map((pid) => [pid, detail.rows.find((r) => r.productId === pid)?.imageUrls ?? []] as const)
                              .filter(([, urls]) => urls.length > 0),
                          ),
                        })
                      }
                      className="text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose"
                    >
                      Editar
                    </button>
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {detail && detail.rows.some((r) => r.status === 'FAILED') && (
                      <button
                        type="button"
                        onClick={() => doRetryBatch(b.batchId as string)}
                        disabled={busyBatch === b.batchId}
                        className="inline-flex items-center gap-1.5 text-[0.78rem] bg-pe-rose text-pe-white px-2.5 py-1 rounded-xs disabled:opacity-50"
                      >
                        {busyBatch === b.batchId ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
                        Reintentar fallidos
                      </button>
                    )}
                    {detail && detail.captionTemplate && (
                      <button
                        type="button"
                        onClick={() =>
                          onRepublish({
                            productIds: detail.productIds,
                            captionTemplate: detail.captionTemplate as string,
                            hashtags: detail.hashtags,
                            campaignLabel: detail.campaignLabel,
                          })
                        }
                        className="text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose"
                      >
                        Editar y volver a publicar
                      </button>
                    )}
                  </div>
                )}

                {!detail && <p className="text-xs text-pe-muted">Cargando…</p>}
                {detail && (
                  <ul className="flex flex-col divide-y divide-pe-border">
                    {detail.rows.map((r) => {
                      const errKey = `${b.batchId}:${r.publicationId}`;
                      return (
                        <li key={r.publicationId} className="py-2 flex flex-wrap items-center gap-3">
                          {r.thumbnailUrl ? (
                            <img src={r.thumbnailUrl} alt="" className="w-8 h-10 object-cover shrink-0" />
                          ) : (
                            <div className="w-8 h-10 bg-pe-surface shrink-0" />
                          )}
                          <span className="flex-1 min-w-0 truncate text-sm">{r.productName}</span>
                          <span className="text-[0.7rem] text-pe-muted shrink-0">{PLATFORM_SHORT[r.platform] ?? r.platform}</span>
                          {(r.imageUrls?.length ?? 0) > 1 && (
                            <span className="text-[0.68rem] text-pe-muted shrink-0">Carrusel · {r.imageUrls.length}</span>
                          )}
                          <StatusPill status={r.status} />
                          <div className="shrink-0 flex items-center gap-2">
                            {r.status === 'FAILED' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => doRetryRow(b.batchId as string, r.publicationId)}
                                  disabled={busyBatch === b.batchId}
                                  className="text-[0.72rem] text-pe-rose hover:underline disabled:opacity-50"
                                >
                                  Reintentar
                                </button>
                                <button
                                  type="button"
                                  onClick={() =>
                                    setOpenError((prev) => {
                                      const next = new Set(prev);
                                      if (next.has(errKey)) next.delete(errKey);
                                      else next.add(errKey);
                                      return next;
                                    })
                                  }
                                  className="text-[0.72rem] text-pe-muted hover:underline"
                                >
                                  ver detalle
                                </button>
                              </>
                            )}
                            {r.status === 'PUBLISHED' && r.externalPermalink && (
                              <a
                                href={r.externalPermalink}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-1 text-[0.72rem] text-pe-muted hover:text-pe-rose"
                              >
                                <ExternalLink size={11} /> Ver en {PLATFORM_NAME[r.platform] ?? r.platform}
                              </a>
                            )}
                          </div>
                          {openError.has(errKey) && (
                            <p className="basis-full text-[0.72rem] text-pe-muted font-mono pl-11">
                              {r.lastErrorCode}: {r.lastErrorMessage}
                            </p>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
