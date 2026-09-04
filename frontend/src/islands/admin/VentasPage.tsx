import { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Search, AlertTriangle, Plus, Undo2 } from 'lucide-react';
import DataTable, { type Column } from './DataTable';
import SaleDetailDrawer from './SaleDetailDrawer';
import RegisterSaleDrawer from './RegisterSaleDrawer';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useCan } from '../../lib/permissions';
import { getAdminSales, type SaleSummaryDto } from '../../lib/api';
import { orderStatusLabel } from '../../lib/orderStatusLabels';

const money = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
});

const PAGE_SIZE = 20;

/**
 * Order statuses worth filtering by. The full enum includes CREATED, which no order has ever
 * persisted in, so offering it would be a filter that always returns nothing.
 */
const STATUS_FILTERS: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todos los estados' },
  { value: 'PENDING_PAYMENT', label: 'Pendiente de pago' },
  { value: 'PAYMENT_UNDER_REVIEW', label: 'Pago en revisión' },
  { value: 'PAID', label: 'Pagado' },
  { value: 'PREPARING_ORDER', label: 'Preparando pedido' },
  { value: 'SHIPPED', label: 'Enviado' },
  { value: 'DELIVERED', label: 'Entregado' },
  { value: 'CANCELLED', label: 'Cancelado' },
];

/**
 * Colour never carries the meaning on its own: each chip says what it is in words too, which is
 * what keeps it readable for a colour-blind seller and in a screen reader.
 */
function DocumentChip({ sale }: { readonly sale: SaleSummaryDto }) {
  const documentable = ['PAID', 'PREPARING_ORDER', 'SHIPPED', 'DELIVERED'].includes(sale.orderStatus);
  if (sale.documentFolio) {
    return (
      <span className="inline-flex items-center text-[0.65rem] tracking-wider uppercase px-2 py-0.5 bg-pe-positive-surface text-pe-positive-ink">
        Boleta {sale.documentFolio}
      </span>
    );
  }
  if (!documentable) {
    return <span className="text-[0.7rem] opacity-40">—</span>;
  }
  return (
    <span className="inline-flex items-center gap-1 text-[0.65rem] tracking-wider uppercase px-2 py-0.5 bg-pe-warning-surface text-pe-warning-ink">
      <AlertTriangle size={11} /> Sin boleta
    </span>
  );
}

function gatewayFlagLabel(flag: string): string {
  return flag === 'CHARGED_BACK' ? 'Contracargo' : 'Reembolsado';
}

export default function VentasPage() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const canReadSales = useCan('orders.read');
  const canIssue = useCan('documents.issue');
  const canVoid = useCan('documents.void');
  const canCancelSale = useCan('orders.update');
  const canRegisterSale = useCan('orders.create');

  const [rows, setRows] = useState<SaleSummaryDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [missingOnly, setMissingOnly] = useState(false);
  const [sortKey, setSortKey] = useState<string | undefined>(undefined);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [selected, setSelected] = useState<SaleSummaryDto | null>(null);
  const [registering, setRegistering] = useState(false);

  const load = useCallback(async () => {
    if (!effectiveToken || !canReadSales) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getAdminSales(
        { q: query, status, missingDocument: missingOnly, page, size: PAGE_SIZE, sortKey, sortDir },
        effectiveToken,
      );
      setRows(result.content);
      setTotal(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudieron cargar las ventas');
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken, canReadSales, query, status, missingOnly, page, sortKey, sortDir]);

  useEffect(() => {
    void load();
  }, [load]);

  // Filters (and a sort change) reset the page: staying on page 4 of a narrower or reordered
  // result set shows an empty table and reads as "no hay ventas".
  useEffect(() => {
    setPage(0);
  }, [query, status, missingOnly, sortKey, sortDir]);

  /** Clicking the active column reverses it; clicking a different one starts at descending --
   * the more useful default for both amount (highest first) and date (most recent first). */
  function handleSort(key: string) {
    if (key === sortKey) {
      setSortDir((dir) => (dir === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  }

  const columns: Column<SaleSummaryDto>[] = [
    {
      key: 'publicReference',
      header: 'Venta',
      render: (row) => (
        <div className="min-w-0">
          <p className="font-medium">{row.publicReference ?? row.orderId.slice(0, 8)}</p>
          <p className="text-[0.7rem] opacity-55 truncate">
            {row.firstItemName ?? 'Sin productos'}
            {row.itemCount > 1 ? ` +${row.itemCount - 1}` : ''}
          </p>
        </div>
      ),
    },
    {
      key: 'customerName',
      header: 'Cliente',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate">{row.customerName ?? 'Sin nombre'}</p>
          <p className="text-[0.7rem] opacity-55 truncate">{row.customerEmail ?? ''}</p>
        </div>
      ),
    },
    {
      key: 'totalAmount',
      header: 'Total',
      width: '120px',
      sortable: true,
      render: (row) => (
        <div className="tabular-nums">
          <p>{row.totalAmount != null ? money.format(row.totalAmount) : '—'}</p>
          <p className="text-[0.68rem] opacity-50">
            {row.netAmount != null && row.taxAmount != null
              ? `Neto ${money.format(row.netAmount)} · IVA ${money.format(row.taxAmount)}`
              : ''}
          </p>
        </div>
      ),
    },
    {
      key: 'orderStatus',
      header: 'Estado',
      width: '150px',
      render: (row) => (
        <div>
          <p className="text-[0.75rem]">{orderStatusLabel(row.orderStatus)}</p>
          <p className="text-[0.68rem] opacity-50">{row.paymentStatus ?? ''}</p>
          {row.paymentGatewayFlag && (
            <span className="mt-1 inline-flex items-center gap-1 text-[0.62rem] tracking-wider uppercase px-1.5 py-0.5 bg-pe-danger-surface text-pe-danger-ink">
              <Undo2 size={10} /> {gatewayFlagLabel(row.paymentGatewayFlag)} — revisar
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'documentFolio',
      header: 'Boleta',
      width: '140px',
      render: (row) => <DocumentChip sale={row} />,
    },
    {
      key: 'createdAt',
      header: 'Fecha',
      width: '130px',
      sortable: true,
      render: (row) => (
        <span className="text-[0.72rem] opacity-70 tabular-nums">
          {new Date(row.createdAt).toLocaleDateString('es-CL', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
          })}
        </span>
      ),
    },
  ];

  if (!canReadSales) {
    return (
      <p className="text-sm opacity-60">
        No tienes permiso para ver las ventas.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <label className="relative flex-1 min-w-[220px]">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 opacity-40" aria-hidden="true" />
          <span className="sr-only">Buscar venta</span>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Referencia, nombre o correo"
            className="w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs pl-9 pr-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30"
          />
        </label>

        <label className="sr-only" htmlFor="ventas-status">Estado</label>
        <select
          id="ventas-status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden"
        >
          {STATUS_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        <button
          type="button"
          onClick={() => setMissingOnly((value) => !value)}
          aria-pressed={missingOnly}
          className={`inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border transition-colors ${
            missingOnly
              ? 'border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)]'
              : 'border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)]'
          }`}
        >
          <AlertTriangle size={13} /> Sin boleta
        </button>

        <button
          type="button"
          onClick={() => void load()}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] transition-colors"
        >
          <RefreshCw size={13} /> Actualizar
        </button>

        {canRegisterSale && (
          <button
            type="button"
            onClick={() => setRegistering(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-ink)] bg-[var(--pe-ink)] text-[var(--pe-surface)] transition-colors"
          >
            <Plus size={13} /> Registrar venta
          </button>
        )}
      </div>

      {error && (
        <p role="alert" className="text-[0.78rem] px-3 py-2 rounded-xs border border-pe-danger/40 text-pe-danger-ink">
          {error}
        </p>
      )}

      <DataTable<SaleSummaryDto>
        columns={columns}
        data={rows}
        keyField="orderId"
        loading={loading}
        emptyMessage={
          missingOnly
            ? 'No hay ventas pagadas sin boleta. Todo lo despachable está declarado.'
            : 'Todavía no hay ventas.'
        }
        page={page}
        pageSize={PAGE_SIZE}
        total={total}
        onPageChange={setPage}
        sortKey={sortKey}
        sortDir={sortDir}
        onSort={handleSort}
        onRowClick={(row) => setSelected(row)}
      />

      {selected && effectiveToken && (
        <SaleDetailDrawer
          sale={selected}
          token={effectiveToken}
          canIssue={canIssue}
          canVoid={canVoid}
          canCancelSale={canCancelSale}
          onClose={() => setSelected(null)}
          onChanged={() => void load()}
        />
      )}

      {registering && effectiveToken && (
        <RegisterSaleDrawer
          token={effectiveToken}
          onClose={() => setRegistering(false)}
          onCreated={() => {
            setRegistering(false);
            setPage(0);
            void load();
          }}
        />
      )}
    </div>
  );
}
