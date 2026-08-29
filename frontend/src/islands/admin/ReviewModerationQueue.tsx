import { useState, useEffect, useCallback } from 'react';
import { Star, Check, Trash2, ChevronDown, ChevronUp } from 'lucide-react';
import { getAdminReviews, approveReview, deleteReview, type ReviewDto } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column, type BulkAction } from './DataTable';

type FilterStatus = 'pending' | 'approved' | 'all';

const FILTER_LABELS: Record<FilterStatus, string> = {
  pending: 'Pendientes',
  approved: 'Aprobadas',
  all: 'Todas',
};

function StarRow({ rating }: { readonly rating: number }) {
  return (
    <div className="flex gap-0.5">
      {Array.from({ length: 5 }).map((_, i) => (
        <Star key={i} size={11}
          className={i < rating ? 'text-pe-rose-ink fill-pe-rose' : 'text-pe-muted'} />
      ))}
    </div>
  );
}

function ExpandableComment({ title, comment }: { readonly title?: string | null; readonly comment?: string | null }) {
  const [expanded, setExpanded] = useState(false);
  const preview = comment ? comment.slice(0, 80) : '';
  const needsExpand = comment && comment.length > 80;

  return (
    <div className="max-w-xs">
      {title && <p className="font-sans text-[0.78rem] font-medium text-pe-charcoal mb-0.5">{title}</p>}
      {comment && (
        <p className="font-sans text-[0.72rem] text-pe-muted leading-relaxed">
          {expanded ? comment : preview}
          {needsExpand && !expanded && '…'}
          {needsExpand && (
            <button
              type="button"
              onClick={() => setExpanded(v => !v)}
              className="ml-1 text-pe-rose-ink hover:underline inline-flex items-center gap-0.5"
            >
              {expanded ? <><ChevronUp size={10} /> menos</> : <><ChevronDown size={10} /> más</>}
            </button>
          )}
        </p>
      )}
    </div>
  );
}

export default function ReviewModerationQueue() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [reviews, setReviews]       = useState<ReviewDto[]>([]);
  const [loading, setLoading]       = useState(true);
  const [filter, setFilter]         = useState<FilterStatus>('pending');
  const [acting, setActing]         = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!effectiveToken) return;
    setLoading(true);
    try {
      let approved: boolean | undefined;
      if (filter === 'pending') {
        approved = false;
      } else if (filter === 'approved') {
        approved = true;
      }
      const data = await getAdminReviews(effectiveToken, approved);
      setReviews(data);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken, filter]);

  useEffect(() => { load(); }, [load]);

  async function handleApprove(id: string) {
    if (!effectiveToken) return;
    setActing(id);
    try {
      await approveReview(id, effectiveToken);
      setReviews(prev => prev.map(r => r.id === id ? { ...r, approved: true } : r));
      if (filter === 'pending') setReviews(prev => prev.filter(r => r.id !== id));
    } catch { alert('Error al aprobar reseña.'); } finally { setActing(null); }
  }

  async function handleDelete(id: string) {
    if (!effectiveToken || !confirm('¿Eliminar esta reseña?')) return;
    setActing(id);
    try {
      await deleteReview(id, effectiveToken);
      setReviews(prev => prev.filter(r => r.id !== id));
    } catch { alert('Error al eliminar reseña.'); } finally { setActing(null); }
  }

  const columns: Column<ReviewDto>[] = [
    {
      key: 'rating',
      header: 'Rating',
      width: '80px',
      render: row => <StarRow rating={row.rating} />,
    },
    {
      key: 'content',
      header: 'Contenido',
      render: row => (
        <ExpandableComment
          title={row.title as string | null}
          comment={row.comment as string | null}
        />
      ),
    },
    {
      key: 'approved',
      header: 'Estado',
      width: '100px',
      render: row => (
        <span className={[
          'font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5',
          row.approved ? 'bg-pe-positive-surface text-pe-positive-ink' : 'bg-pe-cream text-pe-muted',
        ].join(' ')}>
          {row.approved ? 'Aprobada' : 'Pendiente'}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Fecha',
      width: '100px',
      render: row => (
        <span className="font-sans text-[0.72rem] text-pe-muted">
          {new Date(row.createdAt as string).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit', year: '2-digit' })}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '120px',
      render: row => (
        <div className="flex gap-2">
          {!row.approved && (
            <button
              type="button"
              onClick={() => handleApprove(row.id as string)}
              disabled={acting === row.id}
              className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider bg-pe-positive text-white px-2 py-1 hover:opacity-90 transition-colors disabled:opacity-50"
              title="Aprobar"
            >
              <Check size={11} /> Aprobar
            </button>
          )}
          <button
            type="button"
            onClick={() => handleDelete(row.id as string)}
            disabled={acting === row.id}
            className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider border border-pe-danger/40 text-pe-danger-ink px-2 py-1 hover:bg-pe-danger-surface transition-colors disabled:opacity-50"
            title="Eliminar"
          >
            <Trash2 size={11} /> Eliminar
          </button>
        </div>
      ),
    },
  ];

  const bulkActions: BulkAction[] = [
    {
      label: 'Aprobar seleccionadas',
      variant: 'default',
      action: async (ids) => {
        if (!effectiveToken) return;
        for (const id of ids) {
          try { await approveReview(id, effectiveToken); } catch { /* continue */ }
        }
        load();
      },
    },
    {
      label: 'Eliminar seleccionadas',
      variant: 'danger',
      action: async (ids) => {
        if (!effectiveToken || !confirm(`¿Eliminar ${ids.length} reseña(s)?`)) return;
        for (const id of ids) {
          try { await deleteReview(id, effectiveToken); } catch { /* continue */ }
        }
        load();
      },
    },
  ];

  return (
    <div>
      {/* Filter tabs */}
      <div className="mb-4 border-b border-pe-black/10">
        <div className="flex gap-0 overflow-x-auto">
        {(['pending', 'approved', 'all'] as FilterStatus[]).map(f => (
          <button
            type="button"
            key={f}
            onClick={() => setFilter(f)}
            className={[
              'whitespace-nowrap px-4 py-2.5 font-sans text-[0.72rem] tracking-[0.12em] uppercase border-b-2 -mb-px transition-colors duration-150',
              filter === f
                ? 'border-pe-rose text-pe-rose-ink'
                : 'border-transparent text-pe-muted hover:text-pe-charcoal',
            ].join(' ')}
          >
            {FILTER_LABELS[f]}
          </button>
        ))}
        </div>
        <span className="block pb-2.5 pt-1 font-sans text-[0.72rem] text-pe-muted">
          {reviews.length} reseña{reviews.length !== 1 ? 's' : ''}
        </span>
      </div>

      <DataTable
        columns={columns}
        data={reviews}
        keyField="id"
        loading={loading}
        emptyMessage={filter === 'pending' ? 'No hay reseñas pendientes de moderación.' : 'No hay reseñas.'}
        selectable
        bulkActions={bulkActions}
      />
    </div>
  );
}
