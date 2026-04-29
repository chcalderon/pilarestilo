import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';
import {
  addCashMovement,
  closeCashRegister,
  getCashRegisterHistory,
  getCurrentCashRegister,
  openCashRegister,
  type CashRegisterDto,
  type Page,
} from '../../lib/api';

type OperationView = 'loading' | 'no_caja' | 'open' | 'closed' | 'open_form';
type Tab = 'operacion' | 'registros';

const CLP = (n: number) =>
  new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(n);

function formatDateTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('es-CL', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function shortId(id?: string | null): string {
  if (!id) return '-';
  return id.slice(0, 8);
}

export default function CajaPage() {
  const { token, user } = useAuthStore();
  const adminScope = user?.role === 'ADMIN' || user?.role === 'SUPERVISOR';

  const [tab, setTab] = useState<Tab>('operacion');
  const [operationView, setOperationView] = useState<OperationView>('loading');
  const [caja, setCaja] = useState<CashRegisterDto | null>(null);
  const [openBalance, setOpenBalance] = useState('');
  const [closeBalance, setCloseBalance] = useState('');
  const [closeNotes, setCloseNotes] = useState('');
  const [movType, setMovType] = useState<'IN' | 'OUT'>('IN');
  const [movAmount, setMovAmount] = useState('');
  const [movDesc, setMovDesc] = useState('');
  const [operationError, setOperationError] = useState('');
  const [busy, setBusy] = useState(false);

  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OPEN' | 'CLOSED'>('ALL');
  const [fromFilter, setFromFilter] = useState('');
  const [toFilter, setToFilter] = useState('');
  const [sellerFilter, setSellerFilter] = useState('');
  const [historyPage, setHistoryPage] = useState<Page<CashRegisterDto> | null>(null);
  const [historyPageNumber, setHistoryPageNumber] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [selectedRegister, setSelectedRegister] = useState<CashRegisterDto | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    if (params.get('tab') === 'registros') {
      setTab('registros');
    }
  }, []);

  function setTabAndUrl(nextTab: Tab) {
    setTab(nextTab);
    if (typeof window === 'undefined') return;
    const url = new URL(window.location.href);
    if (nextTab === 'registros') {
      url.searchParams.set('tab', 'registros');
    } else {
      url.searchParams.delete('tab');
    }
    window.history.replaceState({}, '', url.toString());
  }

  async function loadCurrent() {
    if (!token) return;
    setOperationView('loading');
    setOperationError('');
    try {
      const data = await getCurrentCashRegister(token);
      setCaja(data);
      setOperationView(data.status === 'OPEN' ? 'open' : 'closed');
    } catch {
      setCaja(null);
      setOperationView('no_caja');
    }
  }

  useEffect(() => {
    void loadCurrent();
  }, [token]);

  async function loadHistory(page = 0) {
    if (!token) return;
    setHistoryLoading(true);
    setHistoryError('');
    try {
      const data = await getCashRegisterHistory(
        token,
        {
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          from: fromFilter || undefined,
          to: toFilter || undefined,
          sellerId: adminScope && sellerFilter.trim() ? sellerFilter.trim() : undefined,
          page,
          size: 10,
        },
        adminScope
      );
      setHistoryPage(data);
      setHistoryPageNumber(page);
      if (data.content.length === 0) {
        setSelectedRegister(null);
      } else if (!selectedRegister) {
        setSelectedRegister(data.content[0]);
      } else {
        const match = data.content.find((r) => r.id === selectedRegister.id);
        setSelectedRegister(match ?? data.content[0]);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No se pudo cargar registros de caja.';
      setHistoryError(message);
    } finally {
      setHistoryLoading(false);
    }
  }

  useEffect(() => {
    if (tab !== 'registros') return;
    void loadHistory(0);
  }, [tab, token]);

  async function openCaja() {
    if (!token || !openBalance) return;
    setBusy(true);
    setOperationError('');
    try {
      await openCashRegister(parseFloat(openBalance), token);
      setOpenBalance('');
      await loadCurrent();
    } catch {
      setOperationError('No se pudo abrir la caja.');
    } finally {
      setBusy(false);
    }
  }

  async function closeCaja() {
    if (!token || !closeBalance) return;
    setBusy(true);
    setOperationError('');
    try {
      await closeCashRegister(parseFloat(closeBalance), token, closeNotes.trim() || undefined);
      setCloseBalance('');
      setCloseNotes('');
      await loadCurrent();
    } catch {
      setOperationError('No se pudo cerrar la caja.');
    } finally {
      setBusy(false);
    }
  }

  async function createMovement() {
    if (!token) return;
    if (!movAmount || !movDesc.trim()) {
      setOperationError('Completa todos los campos.');
      return;
    }
    setBusy(true);
    setOperationError('');
    try {
      await addCashMovement(movType, parseFloat(movAmount), movDesc.trim(), token);
      setMovAmount('');
      setMovDesc('');
      await loadCurrent();
    } catch {
      setOperationError('No se pudo agregar el movimiento.');
    } finally {
      setBusy(false);
    }
  }

  function renderOperationTab() {
    if (operationView === 'loading') {
      return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;
    }

    if (operationView === 'no_caja' || operationView === 'open_form') {
      return (
        <div className="max-w-sm space-y-4">
          <p className="text-pe-charcoal/60 text-sm">No tienes caja abierta.</p>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
              Balance inicial (CLP)
            </label>
            <input
              type="number"
              value={openBalance}
              onChange={(e) => setOpenBalance(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            />
          </div>
          {operationError && <p className="text-red-500 text-sm">{operationError}</p>}
          <button
            onClick={openCaja}
            disabled={busy || !openBalance}
            className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
          >
            {busy ? 'Abriendo...' : 'Abrir caja'}
          </button>
        </div>
      );
    }

    if (!caja) return null;

    if (operationView === 'closed') {
      return (
        <div className="max-w-lg space-y-4">
          <div className="border border-[#EDE3D8] p-4 space-y-2">
            <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Resumen de cierre</p>
            <div className="grid grid-cols-2 gap-2 text-sm">
              <span className="text-pe-charcoal/60">Apertura</span>
              <span className="text-right">{formatDateTime(caja.openedAt)}</span>
              <span className="text-pe-charcoal/60">Cierre</span>
              <span className="text-right">{formatDateTime(caja.closedAt)}</span>
              <span className="text-pe-charcoal/60">Balance inicial</span>
              <span className="text-right">{CLP(caja.openingBalance)}</span>
              <span className="text-pe-charcoal/60">Balance esperado</span>
              <span className="text-right">{CLP(caja.expectedBalance)}</span>
              <span className="text-pe-charcoal/60">Balance declarado</span>
              <span className="text-right">{CLP(caja.closingBalance ?? 0)}</span>
              <span className="text-pe-charcoal/60">Diferencia</span>
              <span className={`text-right font-medium ${(caja.difference ?? 0) === 0 ? 'text-green-600' : 'text-red-500'}`}>
                {CLP(caja.difference ?? 0)}
              </span>
            </div>
          </div>
          <button
            onClick={() => setOperationView('open_form')}
            className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors"
          >
            Abrir nueva caja
          </button>
        </div>
      );
    }

    return (
      <div className="max-w-2xl space-y-6">
        <div className="border border-[#EDE3D8] p-4 flex justify-between items-center">
          <div>
            <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Caja abierta</p>
            <p className="text-2xl font-display text-pe-black mt-1">{CLP(caja.expectedBalance)}</p>
            <p className="text-xs text-pe-charcoal/50 mt-0.5">Balance esperado actual</p>
          </div>
          <span className="px-3 py-1 bg-green-100 text-green-700 text-xs tracking-widest uppercase">Abierta</span>
        </div>

        <div>
          <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Movimientos</p>
          {caja.movements.length === 0 && (
            <p className="text-pe-charcoal/40 text-sm">Sin movimientos aun.</p>
          )}
          <ul className="divide-y divide-[#EDE3D8]">
            {caja.movements.map((movement) => (
              <li key={movement.id} className="flex items-center justify-between py-2.5 text-sm">
                <div>
                  <span className={`text-[10px] tracking-widest uppercase mr-2 ${
                    movement.type === 'SALE' || movement.type === 'IN' ? 'text-green-600' : 'text-red-500'
                  }`}>{movement.type}</span>
                  <span className="text-pe-charcoal/70">{movement.description}</span>
                </div>
                <span className={`font-medium ${movement.type === 'SALE' || movement.type === 'IN' ? 'text-green-600' : 'text-red-500'}`}>
                  {movement.type === 'OUT' || movement.type === 'REFUND' ? '-' : '+'}{CLP(movement.amount)}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <div className="border border-[#EDE3D8] p-4 space-y-3">
          <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Agregar movimiento</p>
          <div className="flex gap-3">
            <select
              value={movType}
              onChange={(e) => setMovType(e.target.value === 'OUT' ? 'OUT' : 'IN')}
              className="border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            >
              <option value="IN">Entrada</option>
              <option value="OUT">Salida</option>
            </select>
            <input
              type="number"
              value={movAmount}
              onChange={(e) => setMovAmount(e.target.value)}
              placeholder="Monto"
              className="flex-1 border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            />
          </div>
          <input
            type="text"
            value={movDesc}
            onChange={(e) => setMovDesc(e.target.value)}
            placeholder="Descripcion"
            className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
          />
          <button
            onClick={createMovement}
            disabled={busy}
            className="bg-[#1A1A1A] text-[#F8F4EF] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
          >
            Agregar
          </button>
        </div>

        <div className="border border-[#EDE3D8] p-4 space-y-3">
          <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Cerrar caja</p>
          <p className="text-xs text-pe-charcoal/50">Balance esperado: {CLP(caja.expectedBalance)}</p>
          <input
            type="number"
            value={closeBalance}
            onChange={(e) => setCloseBalance(e.target.value)}
            placeholder="Balance declarado"
            className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
          />
          <textarea
            value={closeNotes}
            onChange={(e) => setCloseNotes(e.target.value)}
            placeholder="Notas de cierre (opcional)"
            rows={3}
            className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
          />
          <button
            onClick={closeCaja}
            disabled={busy || !closeBalance}
            className="border border-[#1A1A1A] text-[#1A1A1A] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-red-50 hover:border-red-400 hover:text-red-600 transition-colors disabled:opacity-50"
          >
            {busy ? 'Cerrando...' : 'Cerrar caja'}
          </button>
        </div>

        {operationError && <p className="text-red-500 text-sm">{operationError}</p>}
      </div>
    );
  }

  function renderHistoryTab() {
    const registers = historyPage?.content ?? [];

    return (
      <div className="space-y-5">
        <div className="border border-[#EDE3D8] p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">Estado</label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as 'ALL' | 'OPEN' | 'CLOSED')}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            >
              <option value="ALL">Todos</option>
              <option value="OPEN">Abierta</option>
              <option value="CLOSED">Cerrada</option>
            </select>
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">Desde</label>
            <input
              type="date"
              value={fromFilter}
              onChange={(e) => setFromFilter(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            />
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">Hasta</label>
            <input
              type="date"
              value={toFilter}
              onChange={(e) => setToFilter(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
            />
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
              {adminScope ? 'ID cajero (opcional)' : 'Accion'}
            </label>
            {adminScope ? (
              <input
                type="text"
                value={sellerFilter}
                onChange={(e) => setSellerFilter(e.target.value)}
                placeholder="UUID cajero"
                className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]"
              />
            ) : (
              <button
                onClick={() => void loadHistory(0)}
                className="w-full bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors"
              >
                Aplicar
              </button>
            )}
          </div>
          {adminScope && (
            <div className="md:col-span-4 flex justify-end">
              <button
                onClick={() => void loadHistory(0)}
                className="bg-[#1A1A1A] text-[#F8F4EF] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors"
              >
                Aplicar filtros
              </button>
            </div>
          )}
        </div>

        {historyError && <p className="text-red-500 text-sm">{historyError}</p>}
        {historyLoading && <p className="text-pe-charcoal/50 text-sm">Cargando registros...</p>}

        <div className="border border-[#EDE3D8] overflow-auto">
          <table className="w-full min-w-[980px] text-sm">
            <thead className="bg-[#F8F4EF]">
              <tr className="text-left text-[10px] tracking-widest uppercase text-pe-charcoal/50">
                <th className="px-3 py-2">Apertura</th>
                <th className="px-3 py-2">Cierre</th>
                <th className="px-3 py-2">Estado</th>
                <th className="px-3 py-2">Inicial</th>
                <th className="px-3 py-2">Esperado</th>
                <th className="px-3 py-2">Declarado</th>
                <th className="px-3 py-2">Diferencia</th>
                <th className="px-3 py-2">Movs</th>
                <th className="px-3 py-2">Detalle</th>
              </tr>
            </thead>
            <tbody>
              {registers.length === 0 && !historyLoading && (
                <tr>
                  <td className="px-3 py-4 text-pe-charcoal/50" colSpan={9}>No hay registros para los filtros seleccionados.</td>
                </tr>
              )}
              {registers.map((register) => (
                <tr key={register.id} className="border-t border-[#EDE3D8]">
                  <td className="px-3 py-2">{formatDateTime(register.openedAt)}</td>
                  <td className="px-3 py-2">{formatDateTime(register.closedAt)}</td>
                  <td className="px-3 py-2">
                    <span className={`px-2 py-0.5 text-[10px] tracking-widest uppercase rounded ${
                      register.status === 'OPEN'
                        ? 'bg-green-100 text-green-700'
                        : 'bg-pe-charcoal/10 text-pe-charcoal/70'
                    }`}>
                      {register.status}
                    </span>
                  </td>
                  <td className="px-3 py-2">{CLP(register.openingBalance)}</td>
                  <td className="px-3 py-2">{CLP(register.expectedBalance)}</td>
                  <td className="px-3 py-2">{CLP(register.closingBalance ?? 0)}</td>
                  <td className={`px-3 py-2 font-medium ${(register.difference ?? 0) === 0 ? 'text-green-700' : 'text-red-600'}`}>
                    {CLP(register.difference ?? 0)}
                  </td>
                  <td className="px-3 py-2">{register.movements.length}</td>
                  <td className="px-3 py-2">
                    <button
                      onClick={() => setSelectedRegister(register)}
                      className="text-xs tracking-widest uppercase text-pe-rose-deep hover:underline"
                    >
                      Ver
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between">
          <p className="text-xs text-pe-charcoal/50">
            Total registros: {historyPage?.totalElements ?? 0}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => void loadHistory(Math.max(0, historyPageNumber - 1))}
              disabled={historyPageNumber === 0 || historyLoading}
              className="border border-[#EDE3D8] px-3 py-1.5 text-xs tracking-widest uppercase disabled:opacity-40"
            >
              Anterior
            </button>
            <button
              onClick={() => void loadHistory(historyPageNumber + 1)}
              disabled={historyLoading || !historyPage || historyPageNumber + 1 >= historyPage.totalPages}
              className="border border-[#EDE3D8] px-3 py-1.5 text-xs tracking-widest uppercase disabled:opacity-40"
            >
              Siguiente
            </button>
          </div>
        </div>

        {selectedRegister && (
          <div className="border border-[#EDE3D8] p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm tracking-widest uppercase text-pe-charcoal/60">
                Detalle registro #{shortId(selectedRegister.id)}
              </h3>
              <span className="text-xs text-pe-charcoal/45">
                Cajero: {shortId(selectedRegister.sellerId)}
              </span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <div>
                <p className="text-pe-charcoal/45 text-xs uppercase tracking-widest">Apertura</p>
                <p>{formatDateTime(selectedRegister.openedAt)}</p>
              </div>
              <div>
                <p className="text-pe-charcoal/45 text-xs uppercase tracking-widest">Cierre</p>
                <p>{formatDateTime(selectedRegister.closedAt)}</p>
              </div>
              <div>
                <p className="text-pe-charcoal/45 text-xs uppercase tracking-widest">Notas</p>
                <p>{selectedRegister.notes?.trim() || '-'}</p>
              </div>
            </div>
            <div className="overflow-auto border border-[#EDE3D8]">
              <table className="w-full min-w-[760px] text-sm">
                <thead className="bg-[#F8F4EF]">
                  <tr className="text-left text-[10px] tracking-widest uppercase text-pe-charcoal/50">
                    <th className="px-3 py-2">Hora</th>
                    <th className="px-3 py-2">Tipo</th>
                    <th className="px-3 py-2">Descripcion</th>
                    <th className="px-3 py-2">Pedido</th>
                    <th className="px-3 py-2">Monto</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedRegister.movements.length === 0 && (
                    <tr>
                      <td className="px-3 py-3 text-pe-charcoal/50" colSpan={5}>Sin movimientos.</td>
                    </tr>
                  )}
                  {selectedRegister.movements.map((movement) => (
                    <tr key={movement.id} className="border-t border-[#EDE3D8]">
                      <td className="px-3 py-2">{formatDateTime(movement.recordedAt)}</td>
                      <td className="px-3 py-2">{movement.type}</td>
                      <td className="px-3 py-2">{movement.description}</td>
                      <td className="px-3 py-2">#{shortId(movement.orderId)}</td>
                      <td className={`px-3 py-2 font-medium ${
                        movement.type === 'OUT' || movement.type === 'REFUND' ? 'text-red-600' : 'text-green-700'
                      }`}>
                        {movement.type === 'OUT' || movement.type === 'REFUND' ? '-' : '+'}{CLP(movement.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="border-b border-[#EDE3D8]">
        <nav className="flex gap-2">
          <button
            onClick={() => setTabAndUrl('operacion')}
            className={`px-4 py-2 text-xs tracking-widest uppercase border-b-2 transition-colors ${
              tab === 'operacion'
                ? 'border-[#B76E79] text-pe-black'
                : 'border-transparent text-pe-charcoal/50 hover:text-pe-charcoal/80'
            }`}
          >
            Operacion
          </button>
          <button
            onClick={() => setTabAndUrl('registros')}
            className={`px-4 py-2 text-xs tracking-widest uppercase border-b-2 transition-colors ${
              tab === 'registros'
                ? 'border-[#B76E79] text-pe-black'
                : 'border-transparent text-pe-charcoal/50 hover:text-pe-charcoal/80'
            }`}
          >
            Registros
          </button>
        </nav>
      </div>

      {tab === 'operacion' ? renderOperationTab() : renderHistoryTab()}
    </div>
  );
}

