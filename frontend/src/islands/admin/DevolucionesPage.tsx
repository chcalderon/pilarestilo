import { useCallback, useEffect, useState } from 'react';
import { RefreshCw, AlertTriangle, Clock } from 'lucide-react';
import DataTable, { type Column } from './DataTable';
import ReturnDetailDrawer from './ReturnDetailDrawer';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useCan } from '../../lib/permissions';
import { getAdminReturns, type ReturnRequestDto } from '../../lib/api';

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const PAGE_SIZE = 20;

const STATUS_LABELS: Record<ReturnRequestDto['status'], string> = {
  REQUESTED: 'Solicitada',
  APPROVED: 'Aprobada',
  RECEIVED: 'Prenda recibida',
  REFUNDED: 'Reembolsada',
  REJECTED: 'Rechazada',
};

const DISPOSITION_LABELS: Record<string, string> = {
  PENDING_RECONDITIONING: 'En reacondicionamiento',
  RESTOCKED: 'De vuelta a la venta',
  DISCARDED: 'Descartada',
};

/**
 * The refund has forty-five days by law. Ten days out it turns red, which is the whole reason the
 * deadline is stored rather than recomputed: a legal countdown has to be visible, not remembered.
 */
function Deadline({ days, closed }: { days: number; closed: boolean }) {
  if (closed) return <span className="text-[0.72rem] opacity-40">—</span>;
  const overdue = days < 0;
  const urgent = days <= 10;
  return (
    <span
      className={`inline-flex items-center gap-1 text-[0.72rem] tabular-nums ${
        overdue ? 'text-red-600 font-medium' : urgent ? 'text-amber-700' : 'opacity-70'
      }`}
    >
      {(overdue || urgent) && <Clock size={12} aria-hidden="true" />}
      {overdue ? `Vencido hace ${Math.abs(days)} d` : `${days} d para reembolsar`}
    </span>
  );
}

export default function DevolucionesPage() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const canRead = useCan('returns.read');
  const canManage = useCan('returns.manage');
  const canRefund = useCan('returns.refund');

  const [rows, setRows] = useState<ReturnRequestDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [openOnly, setOpenOnly] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ReturnRequestDto | null>(null);

  const load = useCallback(async () => {
    if (!effectiveToken || !canRead) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getAdminReturns({ openOnly, page, size: PAGE_SIZE }, effectiveToken);
      setRows(result.content);
      setTotal(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudieron cargar las devoluciones');
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

  const columns: Column<ReturnRequestDto>[] = [
    {
      key: 'kind',
      header: 'Tipo',
      width: '120px',
      render: (row) =>
        row.kind === 'RETRACTO' ? (
          // Named apart because the shop cannot refuse it: it is a right, not a request.
          <span className="inline-flex items-center text-[0.65rem] tracking-wider uppercase px-2 py-0.5 bg-[var(--pe-surface-soft)] border border-[var(--pe-border)]">
            Retracto
          </span>
        ) : (
          <span className="text-[0.72rem] opacity-70">Devolución</span>
        ),
    },
    {
      key: 'reason',
      header: 'Motivo',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate">{row.reason ?? 'Sin motivo'}</p>
          <p className="text-[0.68rem] opacity-50 tabular-nums">
            {new Date(row.requestedAt).toLocaleDateString('es-CL')}
          </p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Estado',
      width: '150px',
      render: (row) => (
        <div>
          <p className="text-[0.75rem]">{STATUS_LABELS[row.status]}</p>
          {row.itemDisposition && (
            <p className="text-[0.68rem] opacity-50">{DISPOSITION_LABELS[row.itemDisposition]}</p>
          )}
        </div>
      ),
    },
    {
      key: 'deadlineAt',
      header: 'Plazo legal',
      width: '170px',
      render: (row) => (
        <Deadline
          days={row.daysUntilDeadline}
          closed={row.status === 'REFUNDED' || row.status === 'REJECTED'}
        />
      ),
    },
    {
      key: 'refundAmount',
      header: 'Reembolso',
      width: '130px',
      render: (row) =>
        row.refundAmount != null ? (
          <div className="tabular-nums">
            <p>{money.format(row.refundAmount)}</p>
            <p className="text-[0.68rem] opacity-50">{row.refundMethod ?? ''}</p>
          </div>
        ) : (
          <span className="text-[0.72rem] opacity-40">Pendiente</span>
        ),
    },
  ];

  if (!canRead) {
    return <p className="text-sm opacity-60">No tienes permiso para ver las devoluciones.</p>;
  }

  const overdue = rows.filter(
    (row) => row.daysUntilDeadline < 0 && row.status !== 'REFUNDED' && row.status !== 'REJECTED',
  ).length;

  return (
    <div className="space-y-4">
      {overdue > 0 && (
        <p
          role="alert"
          className="flex items-center gap-2 text-[0.8rem] px-3 py-2 rounded-xs border border-red-300/60 text-red-600"
        >
          <AlertTriangle size={14} aria-hidden="true" />
          {overdue === 1
            ? 'Hay 1 devolución con el plazo legal de reembolso vencido.'
            : `Hay ${overdue} devoluciones con el plazo legal de reembolso vencido.`}
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
          Solo abiertas
        </button>
        <button
          type="button"
          onClick={() => void load()}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] transition-colors"
        >
          <RefreshCw size={13} /> Actualizar
        </button>
        <span className="text-[0.72rem] opacity-50 ml-auto">
          {openOnly ? 'Ordenadas por plazo: lo que vence antes va primero' : 'Todas, más recientes primero'}
        </span>
      </div>

      {error && (
        <p role="alert" className="text-[0.78rem] px-3 py-2 rounded-xs border border-red-300/60 text-red-600">
          {error}
        </p>
      )}

      <DataTable<ReturnRequestDto>
        columns={columns}
        data={rows}
        keyField="id"
        loading={loading}
        emptyMessage={
          openOnly ? 'No hay devoluciones abiertas.' : 'Todavía no hay devoluciones.'
        }
        page={page}
        pageSize={PAGE_SIZE}
        total={total}
        onPageChange={setPage}
        onRowClick={(row) => setSelected(row)}
      />

      {selected && effectiveToken && (
        <ReturnDetailDrawer
          request={selected}
          token={effectiveToken}
          canManage={canManage}
          canRefund={canRefund}
          onClose={() => setSelected(null)}
          onChanged={(updated) => {
            setSelected(updated);
            void load();
          }}
        />
      )}
    </div>
  );
}
