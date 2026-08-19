import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import DataTable, { type Column } from './DataTable';
import SupresionDrawer from './SupresionDrawer';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useCan } from '../../lib/permissions';
import { getDeletionRequests, type DeletionRequestDto } from '../../lib/api';

const PAGE_SIZE = 20;

/**
 * The Ley 21.719 gives the shop thirty days to answer a request about somebody's data. Amber from
 * twenty on, red past thirty: a legal clock that is only visible once it has run out is not a
 * warning, it is a record of the miss.
 */
const ANSWER_DAYS = 30;
const WARN_DAYS = 20;

const STATUS_LABELS: Record<DeletionRequestDto['status'], string> = {
  REQUESTED: 'Esperando respuesta',
  ANONYMISED: 'Anonimizada',
  REFUSED: 'Rechazada',
};

function Waiting({ days, open }: { days: number; open: boolean }) {
  if (!open) return <span className="text-[0.72rem] opacity-40">—</span>;
  const late = days > ANSWER_DAYS;
  const soon = days >= WARN_DAYS;
  return (
    <span
      className={`inline-flex items-center gap-1 text-[0.72rem] tabular-nums ${
        late ? 'text-red-600 font-medium' : soon ? 'text-amber-700' : 'opacity-70'
      }`}
    >
      {(late || soon) && <AlertTriangle size={12} aria-hidden="true" />}
      {late ? `${days} días: fuera de plazo` : `${days} de ${ANSWER_DAYS} días`}
    </span>
  );
}

export default function PrivacidadPage() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const canRead = useCan('privacy.read');
  const canResolve = useCan('privacy.resolve');

  const [rows, setRows] = useState<DeletionRequestDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [openOnly, setOpenOnly] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<DeletionRequestDto | null>(null);

  const load = useCallback(async () => {
    if (!effectiveToken || !canRead) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getDeletionRequests({ openOnly, page, size: PAGE_SIZE }, effectiveToken);
      setRows(result.content);
      setTotal(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudieron cargar las solicitudes');
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken, canRead, openOnly, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setPage(0);
  }, [openOnly]);

  const columns: Column<DeletionRequestDto>[] = [
    {
      key: 'customer',
      header: 'Quién pide',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate">{row.customerName ?? 'Sin nombre'}</p>
          <p className="text-[0.68rem] opacity-50 truncate">{row.customerEmail ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'reason',
      header: 'Motivo',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate">{row.reason || 'No dio motivo'}</p>
          <p className="text-[0.68rem] opacity-50 tabular-nums">
            {new Date(row.requestedAt).toLocaleDateString('es-CL')}
          </p>
        </div>
      ),
    },
    {
      key: 'daysWaiting',
      header: 'Plazo legal',
      width: '190px',
      render: (row) => <Waiting days={row.daysWaiting} open={row.status === 'REQUESTED'} />,
    },
    {
      key: 'status',
      header: 'Estado',
      width: '160px',
      // Text, never colour alone: the difference between anonymised and refused is the whole record.
      render: (row) => <span className="text-[0.75rem]">{STATUS_LABELS[row.status]}</span>,
    },
  ];

  if (!canRead) {
    return <p className="text-sm opacity-60">No tienes permiso para ver las solicitudes de datos.</p>;
  }

  const late = rows.filter((row) => row.status === 'REQUESTED' && row.daysWaiting > ANSWER_DAYS).length;

  return (
    <div className="space-y-4">
      {late > 0 && (
        <p
          role="alert"
          className="flex items-center gap-2 text-[0.8rem] px-3 py-2 rounded-xs border border-red-300/60 text-red-600"
        >
          <AlertTriangle size={14} aria-hidden="true" />
          {late === 1
            ? 'Hay 1 solicitud pasada del plazo de 30 días para responder.'
            : `Hay ${late} solicitudes pasadas del plazo de 30 días para responder.`}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setOpenOnly((value) => !value)}
          aria-pressed={openOnly}
          className={`inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border transition-colors ${
            openOnly
              ? 'border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)]'
              : 'border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)]'
          }`}
        >
          Solo pendientes
        </button>
        <button
          type="button"
          onClick={() => void load()}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] transition-colors"
        >
          <RefreshCw size={13} /> Actualizar
        </button>
        <span className="text-[0.72rem] opacity-50 ml-auto">
          {openOnly ? 'La que lleva más tiempo esperando va primero' : 'Todas, incluidas las ya resueltas'}
        </span>
      </div>

      {error && (
        <p role="alert" className="text-[0.78rem] px-3 py-2 rounded-xs border border-red-300/60 text-red-600">
          {error}
        </p>
      )}

      <DataTable<DeletionRequestDto>
        columns={columns}
        data={rows}
        keyField="id"
        loading={loading}
        emptyMessage={
          openOnly
            ? 'Nadie ha pedido que borren sus datos. Cuando alguien lo pida, aparece acá.'
            : 'Todavía no hay solicitudes de supresión.'
        }
        page={page}
        pageSize={PAGE_SIZE}
        total={total}
        onPageChange={setPage}
        onRowClick={(row) => setSelected(row)}
      />

      {selected && effectiveToken && (
        <SupresionDrawer
          request={selected}
          token={effectiveToken}
          canResolve={canResolve}
          onClose={() => setSelected(null)}
          onResolved={(updated) => {
            setSelected(updated);
            void load();
          }}
        />
      )}
    </div>
  );
}
