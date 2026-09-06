import { useEffect, useState } from 'react';
import { ChevronRight, ExternalLink, Loader2 } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  getCampaigns,
  getCampaignDetail,
  refreshCampaignMetrics,
  type CampaignSummary,
  type CampaignDetail,
  type CampaignPostRow,
} from '../../lib/api';

const NF = new Intl.NumberFormat('es-CL', { notation: 'compact', maximumFractionDigits: 1 });
const PLATFORM_SHORT: Record<string, string> = { INSTAGRAM: 'IG', FACEBOOK: 'FB' };
const PLATFORM_NAME: Record<string, string> = { INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook' };

function metricCell(row: CampaignPostRow) {
  if (row.metrics) {
    return `Impresiones ${NF.format(row.metrics.impressions ?? 0)} · Reach ${NF.format(row.metrics.reach ?? 0)}`
      + ` · Likes ${NF.format(row.metrics.likes ?? 0)} · Comentarios ${NF.format(row.metrics.comments ?? 0)}`;
  }
  if (row.fetchError) {
    return null;
  }
  return 'Sin métricas aún';
}

export default function CampanasTab() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [campaigns, setCampaigns] = useState<CampaignSummary[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [detail, setDetail] = useState<CampaignDetail | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function load() {
    void getCampaigns(effectiveToken).then(setCampaigns).catch(() => setCampaigns([]));
  }
  useEffect(load, [effectiveToken]);

  async function toggle(label: string) {
    if (open === label) {
      setOpen(null);
      return;
    }
    setOpen(label);
    setDetail(null);
    setDetail(await getCampaignDetail(label, effectiveToken));
  }

  async function refresh(label: string) {
    setBusy(label);
    setNotice(null);
    try {
      const r = await refreshCampaignMetrics(label, effectiveToken);
      setNotice(`Métricas actualizadas (${r.refreshed}, ${r.failed} con error).`);
      load();
      if (open === label) {
        setDetail(await getCampaignDetail(label, effectiveToken));
      }
    } finally {
      setBusy(null);
    }
  }

  if (campaigns?.length === 0) {
    return (
      <p className="text-sm text-pe-muted">
        Aún no hay campañas. Poné una etiqueta de campaña al publicar.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {notice && <output className="text-xs text-pe-muted">{notice}</output>}
      {(campaigns ?? []).map((c) => (
        <div key={c.label} className="border border-pe-border rounded-xs">
          <div className="flex items-start justify-between gap-3 p-3">
            <button type="button" onClick={() => toggle(c.label)} className="flex-1 min-w-0 text-left">
              <span className="flex items-center gap-1.5 font-sans text-sm">
                <ChevronRight
                  size={14}
                  className={open === c.label ? 'rotate-90 transition-transform' : 'transition-transform'}
                />
                {c.label}
                <span className="text-[0.7rem] text-pe-muted">{c.platforms.map((p) => PLATFORM_SHORT[p] ?? p).join(' · ')}</span>
              </span>
              <span className="mt-0.5 block text-xs text-pe-muted">
                {c.totalPosts} posts · {c.published} publicados · {c.failed} fallidos
                {c.postsWithError > 0 ? ` · ${c.postsWithError} sin métrica` : ''}
              </span>
              <span className="mt-0.5 block text-xs text-pe-muted">
                Impresiones {NF.format(c.totals.impressions)} · Reach {NF.format(c.totals.reach)} ·
                {' '}Likes {NF.format(c.totals.likes)} · Comentarios {NF.format(c.totals.comments)}
              </span>
            </button>
            <button
              type="button"
              onClick={() => refresh(c.label)}
              disabled={busy === c.label}
              className="shrink-0 inline-flex items-center gap-1.5 text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose disabled:opacity-50"
            >
              {busy === c.label && <Loader2 size={12} className="animate-spin" />}
              {busy === c.label ? 'Actualizando…' : 'Actualizar métricas'}
            </button>
          </div>

          {open === c.label && (
            !detail ? (
              <p className="border-t border-pe-border p-3 text-xs text-pe-muted">Cargando…</p>
            ) : (
              <ul className="border-t border-pe-border divide-y divide-pe-border">
                {detail.posts.map((p) => (
                  <li key={p.publicationId} className="p-3 flex flex-wrap items-center gap-3 text-sm">
                    {p.thumbnailUrl ? (
                      <img src={p.thumbnailUrl} alt="" className="w-8 h-10 object-cover shrink-0" />
                    ) : (
                      <div className="w-8 h-10 bg-pe-surface shrink-0" />
                    )}
                    <span className="flex-1 min-w-0 truncate">{p.productName}</span>
                    <span className="text-[0.7rem] text-pe-muted shrink-0">{PLATFORM_NAME[p.platform] ?? p.platform}</span>
                    <span className="text-[0.72rem] text-pe-muted">
                      {metricCell(p) ?? <span title={p.fetchError ?? undefined}>No disponible</span>}
                    </span>
                    {p.externalPermalink && (
                      <a
                        href={p.externalPermalink}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-[0.72rem] text-pe-rose hover:underline shrink-0"
                      >
                        <ExternalLink size={11} /> Ver en {PLATFORM_NAME[p.platform] ?? p.platform}
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            )
          )}
        </div>
      ))}
    </div>
  );
}
