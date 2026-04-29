import { useEffect, useMemo, useState } from 'react';
import { Loader2, Search, X } from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';
import {
  getDispatchHistory,
  getOrderById,
  type DispatchHistoryRowDto,
  type OrderDto,
} from '../../lib/api';

interface QueueDispatchDto {
  id: string;
  orderId: string;
  dispatcherId: string | null;
  status: string;
  carrier: string | null;
  trackingCode: string | null;
  notes: string | null;
  createdAt: string;
}

function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default function DespachosPage() {
  const { token, user } = useAuthStore();
  const [dispatches, setDispatches] = useState<QueueDispatchDto[]>([]);
  const [loadingQueue, setLoadingQueue] = useState(true);
  const [active, setActive] = useState<QueueDispatchDto | null>(null);
  const [carrier, setCarrier] = useState('');
  const [tracking, setTracking] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const [mode, setMode] = useState<'operacion' | 'historial'>('operacion');
  const [historyRows, setHistoryRows] = useState<DispatchHistoryRowDto[]>([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historySize] = useState(20);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [fromDate, setFromDate] = useState(() => toDateInputValue(new Date(Date.now() - 29 * 24 * 60 * 60 * 1000)));
  const [toDate, setToDate] = useState(() => toDateInputValue(new Date()));

  const [orderModalOpen, setOrderModalOpen] = useState(false);
  const [orderDetail, setOrderDetail] = useState<OrderDto | null>(null);
  const [orderDetailLoading, setOrderDetailLoading] = useState(false);
  const [orderDetailError, setOrderDetailError] = useState('');

  const permissions = user?.permissions ?? [];
  const canViewHistory = user?.role === 'ADMIN' || permissions.includes('despachos');

  async function loadQueue() {
    if (!token) return;
    setLoadingQueue(true);
    try {
      const r = await fetch('/api/despachos', { headers: { Authorization: `Bearer ${token}` } });
      if (!r.ok) {
        setDispatches([]);
        return;
      }
      const data: QueueDispatchDto[] = await r.json();
      setDispatches(data);
    } finally {
      setLoadingQueue(false);
    }
  }

  async function loadHistory(nextPage = historyPage) {
    if (!token || !canViewHistory) return;
    setHistoryLoading(true);
    setHistoryError('');
    try {
      const page = await getDispatchHistory(token, {
        from: fromDate || undefined,
        to: toDate || undefined,
        page: nextPage,
        size: historySize,
      });
      setHistoryRows(page.content ?? []);
      setHistoryPage(page.number ?? nextPage);
      setHistoryTotalPages(page.totalPages ?? 0);
    } catch (e) {
      setHistoryRows([]);
      setHistoryTotalPages(0);
      setHistoryError(e instanceof Error ? e.message : 'No se pudo cargar historial.');
    } finally {
      setHistoryLoading(false);
    }
  }

  useEffect(() => {
    void loadQueue();
  }, [token]);

  useEffect(() => {
    if (mode === 'historial') {
      void loadHistory(0);
    }
  }, [mode, token, canViewHistory]);

  async function claim(id: string) {
    if (!token) return;
    setBusy(true);
    await fetch(`/api/despachos/${id}/claim`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    await loadQueue();
    setBusy(false);
  }

  async function unclaim(id: string) {
    if (!token) return;
    setBusy(true);
    await fetch(`/api/despachos/${id}/unclaim`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    await loadQueue();
    setBusy(false);
  }

  async function dispatchOrder(id: string) {
    if (!token) return;
    if (!carrier || !tracking) {
      setError('Ingresa carrier y código de seguimiento.');
      return;
    }
    setBusy(true);
    setError('');
    await fetch(`/api/despachos/${id}/dispatch`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ carrier, trackingCode: tracking }),
    });
    setActive(null);
    setCarrier('');
    setTracking('');
    await loadQueue();
    setBusy(false);
  }

  async function openOrderDetail(orderId: string) {
    if (!token) return;
    setOrderModalOpen(true);
    setOrderDetail(null);
    setOrderDetailError('');
    setOrderDetailLoading(true);
    try {
      const order = await getOrderById(orderId, token);
      setOrderDetail(order);
    } catch (e) {
      setOrderDetailError(e instanceof Error ? e.message : 'No se pudo cargar la orden.');
    } finally {
      setOrderDetailLoading(false);
    }
  }

  const pending = dispatches.filter((d) => d.status === 'PENDING');
  const inProgress = dispatches.filter((d) => d.status === 'IN_PROGRESS');
  const done = dispatches.filter((d) => ['DISPATCHED', 'DELIVERED', 'FAILED'].includes(d.status));

  const searchedHistoryRows = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return historyRows;
    return historyRows.filter((row) => Object.values(row).some((value) => String(value ?? '').toLowerCase().includes(term)));
  }, [historyRows, searchTerm]);

  return (
    <div className="space-y-8">
      {canViewHistory && (
        <div className="flex items-center gap-2">
          <button
            onClick={() => setMode('operacion')}
            className={`px-3 py-2 text-xs tracking-widest uppercase border ${
              mode === 'operacion'
                ? 'bg-[#1A1A1A] text-[#F8F4EF] border-[#1A1A1A]'
                : 'border-[#EDE3D8] text-pe-charcoal/60'
            }`}
          >
            Operación
          </button>
          <button
            onClick={() => setMode('historial')}
            className={`px-3 py-2 text-xs tracking-widest uppercase border ${
              mode === 'historial'
                ? 'bg-[#1A1A1A] text-[#F8F4EF] border-[#1A1A1A]'
                : 'border-[#EDE3D8] text-pe-charcoal/60'
            }`}
          >
            Historial
          </button>
        </div>
      )}

      {mode === 'operacion' && (
        <>
          {loadingQueue ? (
            <div className="flex items-center gap-2 text-sm text-pe-charcoal/60">
              <Loader2 size={16} className="animate-spin" />
              Cargando despachos...
            </div>
          ) : (
            <>
              {inProgress.length > 0 && (
                <section>
                  <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">En progreso</h2>
                  <ul className="space-y-3">
                    {inProgress.map((d) => (
                      <li key={d.id} className="border border-[#EDE3D8] p-4">
                        <p className="text-sm text-pe-charcoal/70 mb-2">Orden {d.orderId.substring(0, 8)}</p>
                        {active?.id === d.id ? (
                          <div className="space-y-2">
                            <input
                              type="text"
                              value={carrier}
                              onChange={(e) => setCarrier(e.target.value)}
                              placeholder="Carrier (ej. Chilexpress)"
                              className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
                            />
                            <input
                              type="text"
                              value={tracking}
                              onChange={(e) => setTracking(e.target.value)}
                              placeholder="Código de seguimiento"
                              className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
                            />
                            {error && <p className="text-red-500 text-xs">{error}</p>}
                            <div className="flex gap-2">
                              <button
                                onClick={() => dispatchOrder(d.id)}
                                disabled={busy}
                                className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
                              >
                                Marcar despachado
                              </button>
                              <button
                                onClick={() => {
                                  void openOrderDetail(d.orderId);
                                }}
                                className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                              >
                                Ver detalle
                              </button>
                              <button
                                onClick={() => setActive(null)}
                                className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                              >
                                Cancelar
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="flex gap-2">
                            <button
                              onClick={() => setActive(d)}
                              className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors"
                            >
                              Despachar
                            </button>
                            <button
                              onClick={() => unclaim(d.id)}
                              disabled={busy}
                              className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                            >
                              Liberar
                            </button>
                            <button
                              onClick={() => {
                                void openOrderDetail(d.orderId);
                              }}
                              className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                            >
                              Ver detalle
                            </button>
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <section>
                <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">
                  Pendientes ({pending.length})
                </h2>
                {pending.length === 0 && <p className="text-pe-charcoal/40 text-sm">Sin órdenes pendientes.</p>}
                <ul className="space-y-2">
                  {pending.map((d) => (
                    <li key={d.id} className="border border-[#EDE3D8] p-4 flex items-center justify-between">
                      <p className="text-sm text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</p>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => {
                            void openOrderDetail(d.orderId);
                          }}
                          className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                        >
                          Ver detalle
                        </button>
                        <button
                          onClick={() => claim(d.id)}
                          disabled={busy}
                          className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
                        >
                          Tomar
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>

              {done.length > 0 && (
                <section>
                  <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Completados</h2>
                  <ul className="space-y-2">
                    {done.map((d) => (
                      <li key={d.id} className="border border-[#EDE3D8] p-3 flex items-center justify-between gap-3 text-sm">
                        <span className="text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</span>
                        <div className="flex items-center gap-2">
                          <span
                            className={`text-xs tracking-widest uppercase ${
                              d.status === 'DELIVERED'
                                ? 'text-green-600'
                                : d.status === 'FAILED'
                                  ? 'text-red-500'
                                  : 'text-blue-500'
                            }`}
                          >
                            {d.status}
                          </span>
                          <button
                            onClick={() => {
                              void openOrderDetail(d.orderId);
                            }}
                            className="border border-[#EDE3D8] px-3 py-1 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50"
                          >
                            Ver detalle
                          </button>
                        </div>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </>
          )}
        </>
      )}

      {mode === 'historial' && canViewHistory && (
        <section className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
            <label className="flex flex-col gap-1 text-xs text-pe-charcoal/60">
              Desde
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="border border-[#EDE3D8] px-3 py-2 text-sm"
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-pe-charcoal/60">
              Hasta
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="border border-[#EDE3D8] px-3 py-2 text-sm"
              />
            </label>
            <div className="md:col-span-2 flex items-end gap-2">
              <div className="relative flex-1">
                <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-pe-charcoal/40" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Buscar por cualquier dato del listado..."
                  className="w-full border border-[#EDE3D8] pl-9 pr-3 py-2 text-sm"
                />
              </div>
              <button
                onClick={() => {
                  setHistoryPage(0);
                  void loadHistory(0);
                }}
                className="px-4 py-2 bg-[#1A1A1A] text-[#F8F4EF] text-xs tracking-widest uppercase"
              >
                Filtrar
              </button>
            </div>
          </div>

          {historyError && <p className="text-sm text-red-600">{historyError}</p>}

          {historyLoading ? (
            <div className="flex items-center gap-2 text-sm text-pe-charcoal/60">
              <Loader2 size={16} className="animate-spin" />
              Cargando historial...
            </div>
          ) : (
            <div className="border border-[#EDE3D8] overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-pe-cream/45">
                  <tr>
                    <th className="text-left px-3 py-2">Fecha venta</th>
                    <th className="text-left px-3 py-2">Transmision a despacho</th>
                    <th className="text-left px-3 py-2">Orden</th>
                    <th className="text-left px-3 py-2">Estado</th>
                    <th className="text-left px-3 py-2">Despacho</th>
                    <th className="text-left px-3 py-2">Envío</th>
                    <th className="text-left px-3 py-2">Vendió</th>
                    <th className="text-left px-3 py-2">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {searchedHistoryRows.length === 0 && (
                    <tr>
                      <td className="px-3 py-4 text-pe-charcoal/50" colSpan={8}>
                        Sin resultados para el rango/filtro actual.
                      </td>
                    </tr>
                  )}
                  {searchedHistoryRows.map((row) => (
                    <tr key={row.id} className="border-t border-[#EDE3D8]">
                      <td className="px-3 py-2 text-pe-charcoal/70">
                        {row.orderCreatedAt ? new Date(row.orderCreatedAt).toLocaleString('es-CL') : '-'}
                      </td>
                      <td className="px-3 py-2 text-pe-charcoal/70">
                        {new Date(row.createdAt).toLocaleString('es-CL')}
                      </td>
                      <td className="px-3 py-2 font-mono text-pe-charcoal/70">{row.orderId.substring(0, 8)}</td>
                      <td className="px-3 py-2">{row.status}</td>
                      <td className="px-3 py-2 text-pe-charcoal/70">
                        {row.carrier || '-'} {row.trackingCode ? `(${row.trackingCode})` : ''}
                      </td>
                      <td className="px-3 py-2 text-pe-charcoal/70">{row.dispatchedBy || 'Sin asignar'}</td>
                      <td className="px-3 py-2 text-pe-charcoal/70">{row.soldBy || 'Web'}</td>
                      <td className="px-3 py-2">
                        <button
                          onClick={() => {
                            void openOrderDetail(row.orderId);
                          }}
                          className="px-2 py-1 border border-[#EDE3D8] text-xs uppercase tracking-wider hover:bg-pe-cream/40"
                        >
                          Ver detalle
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex items-center justify-between">
            <p className="text-xs text-pe-charcoal/50">
              Página {historyPage + 1} de {Math.max(historyTotalPages, 1)}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  const next = Math.max(historyPage - 1, 0);
                  void loadHistory(next);
                }}
                disabled={historyPage <= 0 || historyLoading}
                className="px-3 py-1.5 border border-[#EDE3D8] text-xs uppercase tracking-wider disabled:opacity-40"
              >
                Anterior
              </button>
              <button
                onClick={() => {
                  const next = historyPage + 1;
                  void loadHistory(next);
                }}
                disabled={historyLoading || historyTotalPages === 0 || historyPage >= historyTotalPages - 1}
                className="px-3 py-1.5 border border-[#EDE3D8] text-xs uppercase tracking-wider disabled:opacity-40"
              >
                Siguiente
              </button>
            </div>
          </div>
        </section>
      )}

      {orderModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/45 flex items-center justify-center p-4">
          <div className="bg-pe-white w-full max-w-2xl border border-[#EDE3D8] max-h-[85vh] overflow-y-auto">
            <div className="flex items-center justify-between px-4 py-3 border-b border-[#EDE3D8]">
              <h3 className="text-base font-medium text-pe-charcoal">Detalle de orden</h3>
              <button
                onClick={() => setOrderModalOpen(false)}
                className="p-1 text-pe-charcoal/60 hover:text-pe-charcoal"
                aria-label="Cerrar"
              >
                <X size={18} />
              </button>
            </div>
            <div className="p-4">
              {orderDetailLoading && (
                <div className="flex items-center gap-2 text-sm text-pe-charcoal/60">
                  <Loader2 size={16} className="animate-spin" />
                  Cargando orden...
                </div>
              )}
              {orderDetailError && <p className="text-sm text-red-600">{orderDetailError}</p>}
              {orderDetail && (
                <div className="space-y-3">
                  <div className="text-xs text-pe-charcoal/70">
                    <p><span className="font-semibold">Orden:</span> {orderDetail.id}</p>
                    <p><span className="font-semibold">Cliente:</span> {orderDetail.customerId}</p>
                    <p><span className="font-semibold">Estado:</span> {orderDetail.status}</p>
                    <p><span className="font-semibold">Fecha:</span> {new Date(orderDetail.createdAt).toLocaleString('es-CL')}</p>
                  </div>
                  <ul className="border border-[#EDE3D8] divide-y divide-[#EDE3D8]">
                    {orderDetail.items.map((item) => (
                      <li key={item.id} className="px-3 py-2 flex items-center justify-between gap-2 text-sm">
                        <span>{item.productName} x{item.quantity}</span>
                        <span className="text-pe-charcoal/70">
                          {new Intl.NumberFormat('es-CL', {
                            style: 'currency',
                            currency: item.unitPrice.currency,
                            maximumFractionDigits: 0,
                          }).format(item.unitPrice.amount)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
