import { useState, useEffect, useCallback } from 'react';
import { Check, X, RefreshCw, ExternalLink } from 'lucide-react';
import { getReviewQueuePayments, approvePayment, rejectPayment, type PaymentDto } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column } from './DataTable';

const STATUS_STYLES: Record<string, string> = {
  PENDING:      'bg-amber-50 text-amber-700',
  UNDER_REVIEW: 'bg-blue-50 text-blue-700',
  APPROVED:     'bg-green-50 text-green-700',
  REJECTED:     'bg-red-50 text-red-500',
  SUBMITTED:    'bg-pe-cream text-pe-charcoal/60',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING:      'Pendiente',
  UNDER_REVIEW: 'En revisión',
  APPROVED:     'Aprobado',
  REJECTED:     'Rechazado',
  SUBMITTED:    'Enviado',
};

type ActionState = { id: string; action: 'approve' | 'reject' } | null;

export default function PaymentReviewQueue() {
  const { token, user } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [payments, setPayments] = useState<PaymentDto[]>([]);
  const [loading, setLoading]   = useState(true);
  const [acting, setActing]     = useState<ActionState>(null);

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
      const data = await getReviewQueuePayments(effectiveToken);
      setPayments(data);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken]);

  useEffect(() => { load(); }, [load]);

  async function handleAction(id: string, action: 'approve' | 'reject') {
    if (!effectiveToken) return;
    const reviewerId = user?.id ?? readReviewerIdFromToken(effectiveToken);
    if (!reviewerId) {
      alert('No se pudo identificar al administrador actual. Vuelve a iniciar sesión.');
      return;
    }

    setActing({ id, action });
    try {
      if (action === 'approve') await approvePayment(id, reviewerId, effectiveToken);
      else await rejectPayment(id, reviewerId, effectiveToken);
      setPayments(prev => prev.filter(p => p.id !== id));
    } catch {
      alert(`Error al ${action === 'approve' ? 'aprobar' : 'rechazar'} el pago.`);
    } finally {
      setActing(null);
    }
  }

  const columns: Column<PaymentDto>[] = [
    {
      key: 'orderId',
      header: 'ID Orden',
      render: row => (
        <span className="font-mono text-[0.72rem] text-pe-charcoal/50">
          {String(row.orderId).slice(0, 8)}…
        </span>
      ),
    },
    {
      key: 'method',
      header: 'Método',
      render: row => (
        <span className="font-sans text-[0.78rem] uppercase tracking-wider text-pe-charcoal/70">
          {row.method}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Estado',
      width: '120px',
      render: row => {
        const status = String(row.status);
        return (
          <span className={[
            'font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5',
            STATUS_STYLES[status] ?? 'text-pe-charcoal/50',
          ].join(' ')}>
            {STATUS_LABELS[status] ?? status}
          </span>
        );
      },
    },
    {
      key: 'proofReference',
      header: 'Comprobante',
      render: row => row.proofReference ? (
        <a
          href={String(row.proofReference)}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 font-sans text-[0.72rem] text-pe-rose-deep hover:underline underline-offset-2"
          onClick={e => e.stopPropagation()}
        >
          <ExternalLink size={11} /> Ver
        </a>
      ) : (
        <span className="text-pe-charcoal/25 text-[0.72rem]">—</span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Fecha',
      width: '120px',
      render: row => (
        <span className="font-sans text-[0.72rem] text-pe-charcoal/40">
          {new Date(String(row.createdAt)).toLocaleDateString('es-CL', {
            day: '2-digit', month: '2-digit', year: '2-digit',
            hour: '2-digit', minute: '2-digit',
          })}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '140px',
      render: row => {
        const id = String(row.id);
        const isActing = acting?.id === id;
        return (
          <div className="flex gap-2">
            <button
              onClick={e => { e.stopPropagation(); handleAction(id, 'approve'); }}
              disabled={!!acting}
              className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider bg-green-600 text-white px-2.5 py-1 hover:bg-green-700 transition-colors disabled:opacity-50"
            >
              {isActing && acting?.action === 'approve' ? '…' : <><Check size={11} /> Aprobar</>}
            </button>
            <button
              onClick={e => { e.stopPropagation(); handleAction(id, 'reject'); }}
              disabled={!!acting}
              className="flex items-center gap-1 font-sans text-[0.65rem] uppercase tracking-wider border border-red-300 text-red-500 px-2.5 py-1 hover:bg-red-50 transition-colors disabled:opacity-50"
            >
              {isActing && acting?.action === 'reject' ? '…' : <><X size={11} /> Rechazar</>}
            </button>
          </div>
        );
      },
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="font-sans text-[0.72rem] text-pe-charcoal/40">
          {payments.length} pago{payments.length !== 1 ? 's' : ''} pendiente{payments.length !== 1 ? 's' : ''}
        </p>
        <button
          onClick={load}
          className="flex items-center gap-1.5 font-sans text-[0.72rem] uppercase tracking-wider text-pe-charcoal/45 hover:text-pe-rose transition-colors"
          aria-label="Actualizar"
        >
          <RefreshCw size={13} /> Actualizar
        </button>
      </div>

      <DataTable
        columns={columns}
        data={payments}
        keyField="id"
        loading={loading}
        emptyMessage="No hay pagos pendientes. Todos los pagos han sido procesados."
      />
    </div>
  );
}
