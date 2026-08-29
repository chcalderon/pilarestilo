import { useEffect, useMemo, useRef, useState } from 'react';
import { FolderUp, Sparkles, RefreshCw, Download, Workflow, CheckCircle2, AlertTriangle, Loader2 } from 'lucide-react';
import {
  createProductAiDraft,
  uploadProductAiDraftImages,
  startProductAiJob,
  listProductAiJobs,
  getProductAiJob,
  retryProductAiJob,
  approvePublishProductAiDraft,
  type ProductAiJobStatus,
  type ProductAiJobSummaryDto,
} from '../../lib/api';
import { readAuthTokenCookie, useAuthStore } from '../../lib/authStore';

type TabKey = 'uploads' | 'processing' | 'campaigns';

const TAB_ITEMS: Array<{ key: TabKey; label: string }> = [
  { key: 'uploads', label: 'Carga masiva' },
  { key: 'processing', label: 'Procesamiento IA' },
  { key: 'campaigns', label: 'Campanas (n8n)' },
];

function StatusBadge({ status }: { readonly status: ProductAiJobStatus }) {
  const cls = {
    PENDING: 'bg-pe-cream text-pe-muted border-pe-black/10',
    PROCESSING: 'bg-blue-50 text-blue-700 border-blue-200',
    SUCCESS: 'bg-pe-positive-surface text-pe-positive-ink border-pe-positive/40',
    ERROR: 'bg-pe-danger-surface text-pe-danger-ink border-pe-danger/40',
  }[status];

  return (
    <span className={`inline-flex items-center rounded-sm px-2 py-0.5 text-[0.64rem] tracking-[0.1em] uppercase border ${cls}`}>
      {status}
    </span>
  );
}

function formatRelativeTime(value: string): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'Ahora';
  if (diffMin < 60) return `Hace ${diffMin} min`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `Hace ${diffHours} h`;
  const diffDays = Math.floor(diffHours / 24);
  return `Hace ${diffDays} d`;
}

export default function PublicacionesMediaPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('uploads');
  const [jobs, setJobs] = useState<ProductAiJobSummaryDto[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>('');
  const [successMessage, setSuccessMessage] = useState<string>('');
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [sourceFolder, setSourceFolder] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  async function refreshJobs() {
    if (!effectiveToken) return;
    setLoadingJobs(true);
    try {
      const data = await listProductAiJobs(effectiveToken);
      setJobs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo cargar la cola de jobs');
    } finally {
      setLoadingJobs(false);
    }
  }

  useEffect(() => {
    void refreshJobs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [effectiveToken]);

  useEffect(() => {
    const shouldPoll = jobs.some((job) => job.status === 'PENDING' || job.status === 'PROCESSING');
    if (!shouldPoll || !effectiveToken) return;
    const id = window.setInterval(() => {
      void refreshJobs();
    }, 5000);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobs, effectiveToken]);

  const totalToday = jobs.length;
  const processingCount = jobs.filter((job) => job.status === 'PROCESSING' || job.status === 'PENDING').length;
  const errorCount = jobs.filter((job) => job.status === 'ERROR').length;
  const hasFailures = errorCount > 0;

  const jobRows = useMemo(
    () =>
      jobs.map((job) => ({
        id: job.jobId,
        draftId: job.draftId,
        groupName: `draft-${job.draftId.slice(0, 8)}`,
        status: job.status,
        updatedAt: formatRelativeTime(job.updatedAt),
        progress: job.progress,
        attempt: job.attempt,
      })),
    [jobs],
  );

  function handleSelectFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    setSelectedFiles(files);
    const first = files[0] as File & { webkitRelativePath?: string };
    if (first?.webkitRelativePath) {
      const parts = first.webkitRelativePath.split('/');
      if (parts.length > 1) {
        setSourceFolder(parts[0]);
      }
    }
  }

  async function handleStartBatch() {
    if (!effectiveToken) {
      setError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    if (selectedFiles.length === 0) {
      setError('Selecciona imagenes para iniciar el flujo.');
      return;
    }
    setUploading(true);
    setError('');
    setSuccessMessage('');
    try {
      const draft = await createProductAiDraft(effectiveToken, {
        name: sourceFolder ? `Lote ${sourceFolder}` : 'Lote IA',
        brand: 'Sin marca',
        condition: 'USED',
        priceAmount: 10000,
        priceCurrency: 'CLP',
      });
      await uploadProductAiDraftImages(draft.draftId, selectedFiles, effectiveToken, sourceFolder || undefined);
      const job = await startProductAiJob(effectiveToken, draft.draftId);
      setSuccessMessage(`Lote creado y job iniciado (${job.jobId.slice(0, 8)}).`);
      setSelectedFiles([]);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      await refreshJobs();
      setActiveTab('processing');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar el procesamiento');
    } finally {
      setUploading(false);
    }
  }

  async function handleRetry(jobId: string) {
    if (!effectiveToken) return;
    setError('');
    try {
      await retryProductAiJob(jobId, effectiveToken);
      await refreshJobs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo reintentar el job');
    }
  }

  async function handleDownload(jobId: string) {
    if (!effectiveToken) return;
    setError('');
    try {
      const job = await getProductAiJob(jobId, effectiveToken);
      const firstWithImage = job.items.find((item) => item.processedWebUrl || item.processedMasterUrl || item.processedThumbUrl);
      const url = firstWithImage?.processedWebUrl ?? firstWithImage?.processedMasterUrl ?? firstWithImage?.processedThumbUrl;
      if (!url) {
        throw new Error('El job aun no tiene imagen lista para descarga');
      }
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo descargar la imagen');
    }
  }

  async function handleApprovePublish(draftId: string) {
    if (!effectiveToken) return;
    setError('');
    try {
      const result = await approvePublishProductAiDraft(draftId, effectiveToken);
      setSuccessMessage(`Producto publicado (${result.productId.slice(0, 8)}).`);
      await refreshJobs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo aprobar/publicar el producto');
    }
  }

  return (
    <section className="flex flex-col gap-4">
      {error && (
        <div className="border border-pe-danger/40 bg-pe-danger-surface text-pe-danger-ink px-3 py-2 text-sm">
          {error}
        </div>
      )}
      {successMessage && (
        <div className="border border-pe-positive/40 bg-pe-positive-surface text-pe-positive-ink px-3 py-2 text-sm">
          {successMessage}
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <article className="border border-pe-black/10 bg-pe-white p-4">
          <p className="font-sans text-[0.64rem] tracking-[0.15em] uppercase text-pe-muted">Lotes</p>
          <p className="font-display text-3xl text-pe-black font-light mt-1">{totalToday}</p>
        </article>
        <article className="border border-pe-black/10 bg-pe-white p-4">
          <p className="font-sans text-[0.64rem] tracking-[0.15em] uppercase text-pe-muted">Procesando</p>
          <p className="font-display text-3xl text-pe-black font-light mt-1">{processingCount}</p>
        </article>
        <article className="border border-pe-black/10 bg-pe-white p-4">
          <p className="font-sans text-[0.64rem] tracking-[0.15em] uppercase text-pe-muted">Errores</p>
          <p className="font-display text-3xl text-pe-black font-light mt-1">{errorCount}</p>
        </article>
      </div>

      <div className="inline-flex w-full overflow-x-auto border border-pe-black/10 bg-pe-white p-1">
        {TAB_ITEMS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={[
              'px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.11em] transition-colors whitespace-nowrap',
              activeTab === tab.key
                ? 'bg-pe-black text-pe-offwhite'
                : 'text-pe-muted hover:text-pe-charcoal',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'uploads' && (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
          <article className="xl:col-span-3 border border-pe-black/10 bg-pe-white p-4">
            <div className="flex items-center gap-2 mb-3">
              <FolderUp size={16} className="text-pe-rose-ink" />
              <h2 className="font-sans text-sm tracking-[0.06em] uppercase text-pe-charcoal">Carga por carpeta</h2>
            </div>
            <div className="border-2 border-dashed border-pe-black/20 p-8 text-center bg-pe-cream/35">
              <p className="font-sans text-sm text-pe-muted">
                Selecciona una carpeta o multiples imagenes y crea un lote IA en segundo plano.
              </p>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                className="hidden"
                onChange={handleSelectFiles}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="mt-4 inline-flex items-center gap-2 bg-pe-rose-action text-pe-offwhite px-4 py-2 text-[0.72rem] tracking-[0.13em] uppercase hover:bg-pe-rose-deep transition-colors"
              >
                <FolderUp size={14} />
                Seleccionar imagenes
              </button>
              {selectedFiles.length > 0 && (
                <p className="mt-3 font-sans text-xs text-pe-muted">
                  {selectedFiles.length} archivo(s) listos {sourceFolder ? `de "${sourceFolder}"` : ''}
                </p>
              )}
              <button
                type="button"
                onClick={() => void handleStartBatch()}
                disabled={uploading || selectedFiles.length === 0}
                className="mt-3 inline-flex items-center gap-2 border border-pe-black/20 bg-pe-black text-pe-offwhite px-4 py-2 text-[0.72rem] tracking-[0.13em] uppercase disabled:opacity-50"
              >
                {uploading ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
                Iniciar procesamiento IA
              </button>
            </div>
          </article>
          <article className="xl:col-span-2 border border-pe-black/10 bg-pe-white p-4">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles size={16} className="text-pe-rose-ink" />
              <h3 className="font-sans text-sm tracking-[0.06em] uppercase text-pe-charcoal">Autorrelleno IA</h3>
            </div>
            <p className="font-sans text-[0.78rem] text-pe-muted">
              Cada imagen infiere texto base y mantiene assets listos para aprobacion/publicacion posterior.
            </p>
            <div className="mt-3 border border-pe-black/10 p-3 bg-pe-cream/30">
              <p className="font-sans text-[0.7rem] uppercase tracking-[0.1em] text-pe-muted mb-1">Reglas activas</p>
              <ul className="font-sans text-[0.76rem] text-pe-muted space-y-1">
                <li>Salida visual 4:5 (1024x1280 por defecto)</li>
                <li>Master + web optimizada + thumbnail</li>
                <li>Descarga de imagen final desde admin</li>
              </ul>
            </div>
          </article>
        </div>
      )}

      {activeTab === 'processing' && (
        <article className="border border-pe-black/10 bg-pe-white p-4">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
            <h2 className="font-sans text-sm tracking-[0.06em] uppercase text-pe-charcoal">Cola de jobs</h2>
            <button
              type="button"
              onClick={() => void refreshJobs()}
              className="inline-flex items-center gap-2 border border-pe-black/15 px-3 py-1.5 text-[0.68rem] uppercase tracking-[0.1em] text-pe-muted hover:text-pe-charcoal"
            >
              <RefreshCw size={12} className={loadingJobs ? 'animate-spin' : ''} />
              Actualizar
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px]">
              <thead>
                <tr className="border-b border-pe-black/10 text-left">
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Lote</th>
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Estado</th>
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Progreso</th>
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Intento</th>
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Actualizado</th>
                  <th className="py-2 pr-3 font-sans text-[0.64rem] uppercase tracking-[0.12em] text-pe-muted">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {jobRows.map((row) => (
                  <tr key={row.id} className="border-b border-pe-black/6">
                    <td className="py-3 pr-3 font-sans text-[0.8rem] text-pe-charcoal">{row.groupName}</td>
                    <td className="py-3 pr-3"><StatusBadge status={row.status} /></td>
                    <td className="py-3 pr-3 font-sans text-[0.78rem] text-pe-muted">{row.progress}%</td>
                    <td className="py-3 pr-3 font-sans text-[0.78rem] text-pe-muted">{row.attempt}</td>
                    <td className="py-3 pr-3 font-sans text-[0.78rem] text-pe-muted">{row.updatedAt}</td>
                    <td className="py-3 pr-3">
                      <div className="inline-flex gap-2">
                        <button
                          type="button"
                          onClick={() => void handleDownload(row.id)}
                          className="inline-flex items-center gap-1 border border-pe-black/12 px-2.5 py-1 text-[0.64rem] uppercase tracking-[0.1em] text-pe-muted"
                        >
                          <Download size={11} />
                          Descargar
                        </button>
                        {row.status === 'ERROR' && (
                          <button
                            type="button"
                            onClick={() => void handleRetry(row.id)}
                            className="inline-flex items-center gap-1 border border-pe-danger/40 px-2.5 py-1 text-[0.64rem] uppercase tracking-[0.1em] text-pe-danger-ink"
                          >
                            <RefreshCw size={11} />
                            Reintentar
                          </button>
                        )}
                        {row.status === 'SUCCESS' && (
                          <button
                            type="button"
                            onClick={() => void handleApprovePublish(row.draftId)}
                            className="inline-flex items-center gap-1 border border-pe-positive/40 px-2.5 py-1 text-[0.64rem] uppercase tracking-[0.1em] text-pe-positive-ink"
                          >
                            <CheckCircle2 size={11} />
                            Aprobar/Publicar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {jobRows.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-6 text-center font-sans text-sm text-pe-muted">
                      Aun no hay jobs de procesamiento.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-2 md:grid-cols-2">
            <div className="border border-pe-positive/40 bg-pe-positive-surface p-3 flex items-start gap-2">
              <CheckCircle2 size={16} className="text-pe-positive-ink mt-0.5" />
              <p className="font-sans text-[0.76rem] text-pe-positive-ink">Lotes exitosos quedan listos para aprobar/publicar en catalogo.</p>
            </div>
            <div className="border border-pe-warning/40 bg-pe-warning-surface p-3 flex items-start gap-2">
              <AlertTriangle size={16} className="text-pe-warning-ink mt-0.5" />
              <p className="font-sans text-[0.76rem] text-pe-warning-ink">Los errores conservan trazabilidad por job_id para analisis y retry.</p>
            </div>
          </div>
        </article>
      )}

      {activeTab === 'campaigns' && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
          <article className="lg:col-span-3 border border-pe-black/10 bg-pe-white p-4">
            <div className="flex items-center gap-2 mb-2">
              <Workflow size={16} className="text-pe-rose-ink" />
              <h2 className="font-sans text-sm tracking-[0.06em] uppercase text-pe-charcoal">Flujos n8n</h2>
            </div>
            <p className="font-sans text-[0.8rem] text-pe-muted mb-3">
              Orquesta publicacion de campanas, reels e historias usando assets aprobados desde este modulo.
            </p>
            <div className="space-y-2">
              <div className="border border-pe-black/10 p-3">
                <p className="font-sans text-[0.7rem] uppercase tracking-[0.1em] text-pe-muted mb-1">Workflow 1</p>
                <p className="font-sans text-[0.8rem] text-pe-charcoal">Catalogo aprobado -&gt; copy IA -&gt; cola de campana</p>
              </div>
              <div className="border border-pe-black/10 p-3">
                <p className="font-sans text-[0.7rem] uppercase tracking-[0.1em] text-pe-muted mb-1">Workflow 2</p>
                <p className="font-sans text-[0.8rem] text-pe-charcoal">Cron programado -&gt; publicar IG/FB -&gt; registrar resultado</p>
              </div>
            </div>
          </article>

          <article className="lg:col-span-2 border border-pe-black/10 bg-pe-white p-4">
            <h3 className="font-sans text-sm tracking-[0.06em] uppercase text-pe-charcoal mb-2">Checklist base</h3>
            <ul className="space-y-1.5 font-sans text-[0.78rem] text-pe-muted">
              <li>Meta app en modo Live</li>
              <li>Credenciales Facebook Graph en n8n</li>
              <li>Webhook seguro para eventos de backend</li>
              <li>Aprobacion humana previa a publicar</li>
            </ul>
            <button
              type="button"
              onClick={() => window.location.assign('/admin/settings?tab=notifications')}
              className="mt-4 w-full inline-flex items-center justify-center gap-2 bg-pe-black text-pe-offwhite px-4 py-2 text-[0.7rem] uppercase tracking-[0.12em] hover:bg-pe-charcoal transition-colors"
            >
              Ir a configuracion n8n
            </button>
          </article>
        </div>
      )}

      {hasFailures && activeTab !== 'processing' && (
        <div className="border border-pe-warning/40 bg-pe-warning-surface p-3 text-pe-warning-ink text-sm">
          Hay jobs con error pendientes de reintento en la pestaña de procesamiento.
        </div>
      )}
    </section>
  );
}
