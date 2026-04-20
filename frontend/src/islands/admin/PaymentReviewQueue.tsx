import { useState, useEffect, useCallback, useMemo } from 'react';
import { Check, X, RefreshCw, ExternalLink } from 'lucide-react';
import {
  getReviewQueuePayments,
  getApprovedPayments,
  approvePayment,
  rejectPayment,
  simulateGatewayPaymentStatus,
  type PaymentDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column } from './DataTable';

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-50 text-amber-700',
  UNDER_REVIEW: 'bg-blue-50 text-blue-700',
  APPROVED: 'bg-green-50 text-green-700',
  REJECTED: 'bg-red-50 text-red-500',
  SUBMITTED: 'bg-pe-cream text-pe-charcoal/60',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  UNDER_REVIEW: 'En revision',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
  SUBMITTED: 'Enviado',
};

const ACTIONABLE_STATUSES = new Set(['SUBMITTED', 'UNDER_REVIEW']);

type TabKey = 'queue' | 'approved';
type ActionState = { id: string; action: 'approve' | 'reject' | 'simulate-approve' | 'simulate-reject' } | null;
type DateSortDirection = 'desc' | 'asc';
type QueueFeedback = { type: 'success' | 'error'; text: string } | null;

export default function PaymentReviewQueue() {
  const { token, user } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [activeTab, setActiveTab] = useState<TabKey>('queue');
  const [queuePayments, setQueuePayments] = useState<PaymentDto[]>([]);
  const [approvedPayments, setApprovedPayments] = useState<PaymentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState<ActionState>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [dateSort, setDateSort] = useState<DateSortDirection>('desc');
  const [feedback, setFeedback] = useState<QueueFeedback>(null);

  function readReviewerIdFromToken(jwt: string | null): string | null {
    if (!jwt) return null;
    try {
      const payloadPart = jwt.split('.')[1];
      if (!payloadPart) return null;
      const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
      const json = atob(padded);
      const payload = JSON.parse(json) as { sub?: string };
      return payload.sub ?? null;
    } catch {
      return null;
    }
  }

  const load = useCallback(async () => {
    if (!effectiveToken) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [queue, approved] = await Promise.all([
        getReviewQueuePayments(effectiveToken),
        getApprovedPayments(effectiveToken),
      ]);
      setQueuePayments(queue);
      setApprovedPayments(approved);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleAction(id: string, action: 'approve' | 'reject') {
    if (!effectiveToken) return;
    const reviewerId = user?.id ?? readReviewerIdFromToken(effectiveToken);
    if (!reviewerId) {
      setFeedback({ type: 'error', text: 'No se pudo identificar al administrador actual. Inicia sesion de nuevo.' });
      return;
    }

    setActing({ id, action });
    setFeedback(null);
    try {
      if (action === 'approve') await approvePayment(id, reviewerId, effectiveToken);
      else await rejectPayment(id, reviewerId, effectiveToken);
      await load();
      setFeedback({ type: 'success', text: action === 'approve' ? 'Pago aprobado.' : 'Pago rechazado.' });
    } catch {
      setFeedback({ type: 'error', text: `Error al ${action === 'approve' ? 'aprobar' : 'rechazar'} el pago.` });
    } finally {
      setActing(null);
    }
  }

  async function handleSimulate(id: string, action: 'simulate-approve' | 'simulate-reject') {
    setActing({ id, action });
    setFeedback(null);
    try {
      const gatewayStatus = action === 'simulate-approve' ? 'APPROVED' : 'FAILED';
      await simulateGatewayPaymentStatus(id, gatewayStatus);
      await load();
      setFeedback({
        type: 'success',
        text: action === 'simulate-approve'
          ? 'Simulacion OK: webhook de aprobacion aplicado.'
          : 'Simulacion OK: webhook de rechazo aplicado.',
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '';
      setFeedback({
        type: 'error',
        text: message || 'No se pudo simular el webhook de pasarela.',
      });
    } finally {
      setActing(null);
    }
  }

  const baseColumns: Column<PaymentDto>[] = [
    {
      key: 'orderId',
      header: 'ID Orden',
      render: (row) => (
        <span className="font-mono text-[0.72rem] text-pe-charcoal/55 break-all">
          {String(row.orderId)}
        </span>
      ),
    },
    {
      key: 'method',
      header: 'Metodo',
      render: (row) => (
        <span className="font-sans text-[0.78rem] uppercase tracking-wider text-pe-charcoal/70">{row.method}</span>
      ),
    },
    {
      key: 'status',
      header: 'Estado',
      width: '120px',
      render: (row) => {
        const status = String(row.status);
        return (
          <span
            className={[
              'font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5',
              STATUS_STYLES[status] ?? 'text-pe-charcoal/50',
            ].join(' ')}
          >
            {STATUS_LABELS[status] ?? status}
          </span>
        );
      },
    },
    {
      key: 'proofReference',
      header: 'Comprobante',
      render: (row) => {
        if (row.proofReference) {
          return (
            <a
              href={String(row.proofReference)}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 font-sans text-[0.72rem] text-pe-rose-deep hover:underline underline-offset-2"
              onClick={(e) => e.stopPropagation()}
            >
              <ExternalLink size={11} /> Ver
            </a>
          );
        }

        if (String(row.status) === 'PENDING') {
          return <span className="font-sans text-[0.68rem] text-pe-charcoal/45">Esperando comprobante</span>;
        }

        return <span className="text-pe-charcoal/25 text-[0.72rem]">-</span>;
      },
    },
    {
      key: 'createdAt',
      header: 'Fecha',
      width: '140px',
      render: (row) => (
        <span className="font-sans text-[0.72rem] text-pe-charcoal/40">
          {new Date(String(row.createdAt)).toLocaleDateString('es-CL', {
            day: '2-digit',
            month: '2-digit',
            year: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          })}
        </span>
      ),
    },
  ];

  const queueColumns: Column<PaymentDto>[] = [
    ...baseColumns,
    {
      key: 'actions',
      header: 'Acciones',
      width: '180px',
      render: (row) => {
        const id = String(row.id);
        const isActing = acting?.id === id;
        const status = String(row.status);
        const canReview = ACTIONABLE_STATUSES.has(status);
        const canSimulate = status === 'PENDING' && String(row.method) === 'PAYMENT_GATEWAY';

        if (!canReview && !canSimulate) {
          return <span className="font-sans text-[0.68rem] text-pe-charcoal/45">Sin accion</span>;
        }

        if (canSimulate) {
          return (
            <div className="flex gap-2">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  void handleSimulate(id, 'simulate-approve');
                }}
                disabled={!!acting}
                className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider bg-green-600 text-white px-2.5 py-1 hover:bg-green-700 transition-colors disabled:opacity-50"
              >
                {isActing && acting?.action === 'simulate-approve' ? '...' : <><Check size={11} /> Sim aprobar</>}
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  void handleSimulate(id, 'simulate-reject');
                }}
                disabled={!!acting}
                className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider border border-red-300 text-red-500 px-2.5 py-1 hover:bg-red-50 transition-colors disabled:opacity-50"
              >
                {isActing && acting?.action === 'simulate-reject' ? '...' : <><X size={11} /> Sim rechazar</>}
              </button>
            </div>
          );
        }

        return (
          <div className="flex gap-2">
            <button
              onClick={(e) => {
                e.stopPropagation();
                void handleAction(id, 'approve');
              }}
              disabled={!!acting}
              className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider bg-green-600 text-white px-2.5 py-1 hover:bg-green-700 transition-colors disabled:opacity-50"
            >
              {isActing && acting?.action === 'approve' ? '...' : <><Check size={11} /> Aprobar</>}
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                void handleAction(id, 'reject');
              }}
              disabled={!!acting}
              className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider border border-red-300 text-red-500 px-2.5 py-1 hover:bg-red-50 transition-colors disabled:opacity-50"
            >
              {isActing && acting?.action === 'reject' ? '...' : <><X size={11} /> Rechazar</>}
            </button>
          </div>
        );
      },
    },
  ];

  const approvedColumns: Column<PaymentDto>[] = [
    ...baseColumns,
    {
      key: 'reviewedAt',
      header: 'Revision',
      width: '140px',
      render: (row) => (
        <span className="font-sans text-[0.72rem] text-pe-charcoal/40">
          {row.reviewedAt
            ? new Date(String(row.reviewedAt)).toLocaleDateString('es-CL', {
              day: '2-digit',
              month: '2-digit',
              year: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
            })
            : '-'}
        </span>
      ),
    },
  ];

  const visibleData = useMemo(() => {
    const source = activeTab === 'queue' ? queuePayments : approvedPayments;
    const term = searchTerm.trim().toLowerCase();

    const filtered = term
      ? source.filter((row) => {
        const searchable = [
          String(row.id),
          String(row.orderId),
          String(row.method),
          String(row.status),
          String(row.proofReference ?? ''),
        ].join(' ').toLowerCase();
        return searchable.includes(term);
      })
      : source;

    return [...filtered].sort((a, b) => {
      const aDateValue = activeTab === 'approved' && a.reviewedAt ? a.reviewedAt : a.createdAt;
      const bDateValue = activeTab === 'approved' && b.reviewedAt ? b.reviewedAt : b.createdAt;
      const aTime = new Date(String(aDateValue)).getTime();
      const bTime = new Date(String(bDateValue)).getTime();
      return dateSort === 'desc' ? bTime - aTime : aTime - bTime;
    });
  }, [activeTab, queuePayments, approvedPayments, searchTerm, dateSort]);

  const canClearFilters = searchTerm.trim().length > 0 || dateSort !== 'desc';

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="inline-flex items-center p-1 bg-pe-cream border border-pe-black/10">
          <button
            type="button"
            onClick={() => setActiveTab('queue')}
            className={[
              'px-3 py-1.5 font-sans text-[0.7rem] tracking-wider uppercase transition-colors',
              activeTab === 'queue' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
            ].join(' ')}
          >
            Por revisar ({queuePayments.length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('approved')}
            className={[
              'px-3 py-1.5 font-sans text-[0.7rem] tracking-wider uppercase transition-colors',
              activeTab === 'approved' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
            ].join(' ')}
          >
            Pagados ({approvedPayments.length})
          </button>
        </div>

        <div className="flex items-center gap-2 ml-auto">
          <input
            type="search"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder={activeTab === 'queue' ? 'Buscar por orden, estado, metodo...' : 'Buscar pago aprobado...'}
            className="w-[220px] max-w-[52vw] bg-pe-white border border-pe-black/15 px-3 py-1.5 font-sans text-[0.75rem] text-pe-charcoal placeholder:text-pe-charcoal/40 focus:outline-none focus:border-pe-rose/45"
            aria-label="Buscar pagos"
          />

          <select
            value={dateSort}
            onChange={(e) => setDateSort(e.target.value as DateSortDirection)}
            className="bg-pe-white border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.72rem] text-pe-charcoal focus:outline-none focus:border-pe-rose/45"
            aria-label="Ordenar por fecha"
          >
            <option value="desc">Fecha: mas reciente</option>
            <option value="asc">Fecha: mas antigua</option>
          </select>

          <button
            type="button"
            onClick={() => {
              setSearchTerm('');
              setDateSort('desc');
            }}
            disabled={!canClearFilters}
            className="px-2.5 py-1.5 font-sans text-[0.72rem] uppercase tracking-wider border border-pe-black/15 text-pe-charcoal/60 hover:text-pe-charcoal hover:bg-pe-cream transition-colors disabled:opacity-45 disabled:cursor-not-allowed"
          >
            Limpiar filtros
          </button>

          <button
            onClick={() => {
              void load();
            }}
            className="flex items-center gap-1.5 font-sans text-[0.72rem] uppercase tracking-wider text-pe-charcoal/45 hover:text-pe-rose transition-colors"
            aria-label="Actualizar"
          >
            <RefreshCw size={13} /> Actualizar
          </button>
        </div>
      </div>

      {feedback && (
        <div
          className={[
            'mb-3 flex items-start justify-between gap-3 border px-3 py-2',
            feedback.type === 'error'
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-green-200 bg-green-50 text-green-700',
          ].join(' ')}
        >
          <p className="font-sans text-[0.74rem]">{feedback.text}</p>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            className="font-sans text-[0.68rem] uppercase tracking-[0.16em] opacity-75 hover:opacity-100"
          >
            Cerrar
          </button>
        </div>
      )}

      {activeTab === 'queue' && (
        <p className="font-sans text-[0.68rem] text-pe-charcoal/45 mb-2">
          Simulacion dev: en pagos `PAYMENT_GATEWAY` pendientes puedes usar `Sim aprobar` o `Sim rechazar`.
        </p>
      )}

      <p className="font-sans text-[0.7rem] text-pe-charcoal/45 mb-2">
        {visibleData.length} resultado{visibleData.length !== 1 ? 's' : ''}
      </p>

      <DataTable
        columns={activeTab === 'queue' ? queueColumns : approvedColumns}
        data={visibleData}
        keyField="id"
        loading={loading}
        emptyMessage={activeTab === 'queue' ? 'No hay pagos por revisar.' : 'No hay pagos aprobados.'}
      />
    </div>
  );
}
