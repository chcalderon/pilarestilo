import { useEffect, useMemo, useRef, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';
import {
  addCashMovement,
  closeCashRegister,
  getCashRegisterHistory,
  getCurrentCashRegister,
  openCashRegister,
  type CashMovementCategory,
  type CashMovementDto,
  type CashMovementType,
  type CashRegisterDto,
  type Page,
} from '../../lib/api';

type OperationView = 'loading' | 'no_caja' | 'open' | 'closed' | 'open_form';
type Tab = 'operacion' | 'registros';
type DrawerCategory = 'EXPENSE' | 'WITHDRAWAL' | 'ADJUSTMENT';

const clpFormatter = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const CLP = (n: number) => clpFormatter.format(n);

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

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('es-CL', { hour: '2-digit', minute: '2-digit' });
}

function shortId(id?: string | null): string {
  if (!id) return '-';
  return id.slice(0, 8);
}

// ─── Category chip ────────────────────────────────────────────────────────────

const CATEGORY_LABELS: Record<CashMovementCategory, string> = {
  INITIAL_BALANCE: 'Apertura',
  CASH_SALE: 'Venta POS',
  WITHDRAWAL: 'Retiro',
  EXPENSE: 'Gasto',
  ADJUSTMENT: 'Ajuste',
  REFUND: 'Reembolso',
};

const CATEGORY_CHIP_CLASS: Record<CashMovementCategory, string> = {
  INITIAL_BALANCE: 'bg-pe-beige text-pe-charcoal dark:bg-pe-black/60 dark:text-pe-beige',
  CASH_SALE: 'bg-green-50 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  WITHDRAWAL: 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  EXPENSE: 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  REFUND: 'bg-orange-50 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  ADJUSTMENT: 'bg-pe-beige text-pe-charcoal dark:bg-pe-black/60 dark:text-pe-beige',
};

function CategoryChip({ category }: { category: CashMovementCategory }) {
  return (
    <span className={`px-2 py-0.5 text-[10px] tracking-widest uppercase rounded-sm ${CATEGORY_CHIP_CLASS[category] ?? 'bg-pe-beige text-pe-charcoal'}`}>
      {CATEGORY_LABELS[category] ?? category}
    </span>
  );
}

// ─── Balance tile ─────────────────────────────────────────────────────────────

function BalanceTile({
  label,
  value,
  className = '',
}: {
  label: string;
  value: string | React.ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex-1 min-w-0 px-5 py-4 ${className}`}>
      <p className="text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">{label}</p>
      <p className="text-2xl font-light text-pe-black dark:text-pe-beige truncate">{value}</p>
    </div>
  );
}

// ─── Side Drawer ─────────────────────────────────────────────────────────────

interface DrawerProps {
  open: boolean;
  category: DrawerCategory;
  onClose: () => void;
  onSubmit: (type: CashMovementType, category: CashMovementCategory, amount: number, description: string) => Promise<void>;
  busy: boolean;
  error: string;
}

const DRAWER_TITLES: Record<DrawerCategory, string> = {
  EXPENSE: 'Registrar gasto',
  WITHDRAWAL: 'Registrar retiro',
  ADJUSTMENT: 'Registrar ajuste',
};

function MovementDrawer({ open, category, onClose, onSubmit, busy, error }: DrawerProps) {
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [adjustType, setAdjustType] = useState<CashMovementType>('OUT');
  const amountRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setAmount('');
      setDescription('');
      setAdjustType('OUT');
      setTimeout(() => amountRef.current?.focus(), 80);
    }
  }, [open, category]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  const resolvedType: CashMovementType = category === 'ADJUSTMENT' ? adjustType : 'OUT';
  const resolvedCategory: CashMovementCategory = category;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const n = parseFloat(amount);
    if (!Number.isFinite(n) || n <= 0 || !description.trim()) return;
    await onSubmit(resolvedType, resolvedCategory, n, description.trim());
  }

  return (
    <>
      {/* Overlay */}
      <button
        type="button"
        aria-label="Cerrar"
        className={`fixed inset-0 bg-pe-black/30 z-40 cursor-default transition-opacity duration-200 ${open ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'}`}
        onClick={onClose}
      />
      {/* Drawer panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={DRAWER_TITLES[category]}
        className={`fixed inset-y-0 right-0 w-80 bg-white dark:bg-[#1A1A1A] shadow-2xl z-50 flex flex-col transition-transform duration-200 ${open ? 'translate-x-0' : 'translate-x-full'}`}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#EDE3D8] dark:border-white/10">
          <p className="text-[11px] tracking-widest uppercase text-pe-charcoal dark:text-pe-beige font-medium">
            {DRAWER_TITLES[category]}
          </p>
          <button
            onClick={onClose}
            className="text-pe-muted dark:text-pe-beige/50 hover:text-pe-charcoal dark:hover:text-pe-beige transition-colors"
            aria-label="Cerrar"
          >
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M3 3l12 12M15 3L3 15"/>
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 flex flex-col gap-5 px-5 py-6 overflow-y-auto">
          {category === 'ADJUSTMENT' && (
            <div>
              <label className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-2">
                Tipo de ajuste
              </label>
              <div className="flex gap-2">
                {(['IN', 'OUT'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setAdjustType(t)}
                    className={`flex-1 py-2 text-xs tracking-widest uppercase border transition-colors ${
                      adjustType === t
                        ? 'bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black border-[#1A1A1A] dark:border-pe-beige'
                        : 'border-[#EDE3D8] dark:border-white/20 text-pe-muted dark:text-pe-beige/60 hover:border-[#B76E79]'
                    }`}
                  >
                    {t === 'IN' ? 'Entrada' : 'Salida'}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div>
            <label htmlFor="drawer-amount" className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-2">
              Monto (CLP)
            </label>
            <input
              id="drawer-amount"
              ref={amountRef}
              type="number"
              min="1"
              step="1"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79] placeholder:text-pe-muted dark:placeholder:text-pe-beige/30"
              required
            />
          </div>

          <div>
            <label htmlFor="drawer-description" className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-2">
              Descripción
            </label>
            <input
              id="drawer-description"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Detalle del movimiento"
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79] placeholder:text-pe-muted dark:placeholder:text-pe-beige/30"
              required
            />
          </div>

          {error && <p className="text-red-500 text-xs">{error}</p>}

          <div className="mt-auto">
            <button
              type="submit"
              disabled={busy || !amount || !description.trim()}
              className="w-full bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-6 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors disabled:opacity-50"
            >
              {busy ? 'Registrando...' : 'Registrar'}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}

// ─── Main component ────────────────────────────────────────────────────────────

export default function CajaPage() {
  const { token, user } = useAuthStore();
  const adminScope = user?.role === 'ADMIN' || user?.role === 'SUPERVISOR';

  const [tab, setTab] = useState<Tab>('operacion');
  const [operationView, setOperationView] = useState<OperationView>('loading');
  const [caja, setCaja] = useState<CashRegisterDto | null>(null);
  const [openBalance, setOpenBalance] = useState('');

  // Close flow
  const [closingMode, setClosingMode] = useState(false);
  const [declaredBalance, setDeclaredBalance] = useState('');
  const [closeNotes, setCloseNotes] = useState('');

  // Movement filter
  const [showPosSales, setShowPosSales] = useState(false);

  // Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerCategory, setDrawerCategory] = useState<DrawerCategory>('EXPENSE');

  const [operationError, setOperationError] = useState('');
  const [drawerError, setDrawerError] = useState('');
  const [operationBusy, setOperationBusy] = useState(false);
  const [drawerBusy, setDrawerBusy] = useState(false);

  // History tab
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
    if (params.get('tab') === 'registros') setTab('registros');
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
    if (!token) return;
    const n = parseFloat(openBalance);
    if (!Number.isFinite(n) || n < 0) return;
    setOperationBusy(true);
    setOperationError('');
    try {
      await openCashRegister(n, token);
      setOpenBalance('');
      setClosingMode(false);
      setDeclaredBalance('');
      await loadCurrent();
    } catch {
      setOperationError('No se pudo abrir la caja.');
    } finally {
      setOperationBusy(false);
    }
  }

  async function closeCaja() {
    if (!token || !declaredBalance) return;
    const declared = parseFloat(declaredBalance);
    if (!Number.isFinite(declared) || declared < 0) return;
    setOperationBusy(true);
    setOperationError('');
    try {
      await closeCashRegister(declared, token, closeNotes.trim() || undefined);
      setDeclaredBalance('');
      setCloseNotes('');
      setClosingMode(false);
      await loadCurrent();
    } catch {
      setOperationError('No se pudo cerrar la caja.');
    } finally {
      setOperationBusy(false);
    }
  }

  async function createMovement(
    type: CashMovementType,
    category: CashMovementCategory,
    amount: number,
    description: string
  ) {
    if (!token) return;
    setDrawerBusy(true);
    setDrawerError('');
    try {
      await addCashMovement(type, category, amount, description, token);
      setDrawerOpen(false);
      await loadCurrent();
    } catch {
      setDrawerError('No se pudo agregar el movimiento.');
    } finally {
      setDrawerBusy(false);
    }
  }

  function openDrawer(cat: DrawerCategory) {
    setDrawerCategory(cat);
    setDrawerError('');
    setDrawerOpen(true);
  }

  // ─── Derived values ────────────────────────────────────────────────────────

  const visibleMovements = useMemo(
    () => (caja?.movements ?? []).filter((m) => m.category !== 'CASH_SALE' || showPosSales),
    [caja?.movements, showPosSales]
  );

  const inSum = useMemo(
    () => (caja?.movements ?? [])
      .filter((m) => m.type === 'IN' && m.category !== 'INITIAL_BALANCE')
      .reduce((acc, m) => acc + m.amount, 0),
    [caja?.movements]
  );

  const outSum = useMemo(
    () => (caja?.movements ?? [])
      .filter((m) => m.type === 'OUT')
      .reduce((acc, m) => acc + m.amount, 0),
    [caja?.movements]
  );

  const declaredNum = parseFloat(declaredBalance);
  const difference = Number.isFinite(declaredNum) && caja
    ? declaredNum - caja.expectedBalance
    : null;

  const differenceColor =
    difference === null
      ? ''
      : difference < 0
        ? 'text-[#B76E79]'
        : difference > 0
          ? 'text-[#6B7A5E]'
          : 'text-pe-charcoal dark:text-pe-beige';

  // ─── Render: Open form / No caja ──────────────────────────────────────────

  function renderOpenForm() {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center gap-6">
        <p className="font-display text-2xl text-pe-muted dark:text-pe-beige/40 italic">
          {operationView === 'no_caja' ? 'Aún no hay caja abierta' : 'Abrir nueva caja'}
        </p>
        <div className="w-full max-w-xs space-y-4">
          <div>
            <label htmlFor="open-balance" className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">
              Balance inicial (CLP)
            </label>
            <input
              id="open-balance"
              type="number"
              value={openBalance}
              onChange={(e) => setOpenBalance(e.target.value)}
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79]"
            />
          </div>
          {operationError && <p className="text-red-500 text-sm">{operationError}</p>}
          <button
            onClick={openCaja}
            disabled={operationBusy || !Number.isFinite(parseFloat(openBalance)) || parseFloat(openBalance) < 0}
            className="w-full bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors disabled:opacity-50"
          >
            {operationBusy ? 'Abriendo...' : 'Abrir caja'}
          </button>
        </div>
      </div>
    );
  }

  // ─── Render: Closed summary ────────────────────────────────────────────────

  function renderClosed() {
    if (!caja) return null;
    return (
      <div className="max-w-lg space-y-4">
        <div className="border border-[#EDE3D8] dark:border-white/10 p-4 space-y-2">
          <p className="text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/40">Resumen de cierre</p>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <span className="text-pe-muted dark:text-pe-beige/60">Apertura</span>
            <span className="text-right text-pe-charcoal dark:text-pe-beige">{formatDateTime(caja.openedAt)}</span>
            <span className="text-pe-muted dark:text-pe-beige/60">Cierre</span>
            <span className="text-right text-pe-charcoal dark:text-pe-beige">{formatDateTime(caja.closedAt)}</span>
            <span className="text-pe-muted dark:text-pe-beige/60">Balance inicial</span>
            <span className="text-right text-pe-charcoal dark:text-pe-beige">{CLP(caja.openingBalance)}</span>
            <span className="text-pe-muted dark:text-pe-beige/60">Balance esperado</span>
            <span className="text-right text-pe-charcoal dark:text-pe-beige">{CLP(caja.expectedBalance)}</span>
            <span className="text-pe-muted dark:text-pe-beige/60">Balance declarado</span>
            <span className="text-right text-pe-charcoal dark:text-pe-beige">{CLP(caja.closingBalance ?? 0)}</span>
            <span className="text-pe-muted dark:text-pe-beige/60">Diferencia</span>
            <span className={`text-right font-medium ${(caja.difference ?? 0) === 0 ? 'text-[#6B7A5E]' : 'text-[#B76E79]'}`}>
              {CLP(caja.difference ?? 0)}
            </span>
          </div>
        </div>
        <button
          onClick={() => setOperationView('open_form')}
          className="bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors"
        >
          Abrir nueva caja
        </button>
      </div>
    );
  }

  // ─── Render: Open caja ─────────────────────────────────────────────────────

  function renderOpenCaja() {
    if (!caja) return null;

    return (
      <div className="space-y-6">
        {/* Status header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span className="w-2 h-2 rounded-full bg-green-500 inline-block" />
            <span className="text-sm text-pe-muted dark:text-pe-beige/70">
              Caja abierta
              <span className="mx-1.5 text-pe-muted dark:text-pe-beige/30">·</span>
              Sesión #{shortId(caja.id)}
              <span className="mx-1.5 text-pe-muted dark:text-pe-beige/30">·</span>
              {formatTime(caja.openedAt)}h
            </span>
          </div>
          <button
            onClick={() => setShowPosSales((v) => !v)}
            className={`text-[10px] tracking-widest uppercase px-3 py-1.5 border transition-colors ${
              showPosSales
                ? 'border-[#B76E79] text-[#B76E79]'
                : 'border-[#EDE3D8] dark:border-white/20 text-pe-muted dark:text-pe-beige/50 hover:border-[#B76E79] hover:text-[#B76E79]'
            }`}
          >
            {showPosSales ? 'Ocultar ventas POS' : 'Mostrar ventas POS'}
          </button>
        </div>

        {/* Balance tiles */}
        <div className="border border-[#EDE3D8] dark:border-white/10 flex divide-x divide-[#EDE3D8] dark:divide-white/10 overflow-auto">
          <BalanceTile label="Saldo inicial" value={CLP(caja.openingBalance)} />
          <BalanceTile label="Entradas" value={CLP(inSum)} />
          <BalanceTile label="Salidas" value={CLP(outSum)} />
          <BalanceTile label="Esperado" value={CLP(caja.expectedBalance)} />
        </div>

        {/* Close flow tiles */}
        {closingMode && (
          <div className="border border-[#EDE3D8] dark:border-white/10 flex divide-x divide-[#EDE3D8] dark:divide-white/10 overflow-auto">
            <div className="flex-1 min-w-0 px-5 py-4">
              <label htmlFor="declared-balance" className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">Declarado</label>
              <input
                id="declared-balance"
                type="number"
                min="0"
                step="1"
                value={declaredBalance}
                onChange={(e) => setDeclaredBalance(e.target.value)}
                placeholder="0"
                className="w-full text-2xl font-light text-pe-black dark:text-pe-beige bg-transparent border-0 border-b border-[#EDE3D8] dark:border-white/20 focus:outline-hidden focus:border-[#B76E79] pb-0.5 placeholder:text-pe-muted dark:placeholder:text-pe-beige/20"
              />
            </div>
            <div className="flex-1 min-w-0 px-5 py-4">
              <p className="text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">Diferencia</p>
              <p className={`text-2xl font-light ${differenceColor}`}>
                {difference !== null ? CLP(difference) : '—'}
              </p>
            </div>
          </div>
        )}

        {/* Secondary CTAs + primary close */}
        <div className="flex flex-wrap gap-3 items-center">
          <button
            onClick={() => openDrawer('EXPENSE')}
            className="border border-[#8E4F58] text-[#8E4F58] dark:border-pe-rose dark:text-pe-rose-ink px-4 py-1.5 text-xs tracking-widest uppercase hover:bg-[#8E4F58] hover:text-white dark:hover:bg-pe-rose dark:hover:text-white transition-colors"
          >
            Registrar gasto
          </button>
          <button
            onClick={() => openDrawer('WITHDRAWAL')}
            className="border border-[#8E4F58] text-[#8E4F58] dark:border-pe-rose dark:text-pe-rose-ink px-4 py-1.5 text-xs tracking-widest uppercase hover:bg-[#8E4F58] hover:text-white dark:hover:bg-pe-rose dark:hover:text-white transition-colors"
          >
            Retiro
          </button>
          <button
            onClick={() => openDrawer('ADJUSTMENT')}
            className="border border-[#8E4F58] text-[#8E4F58] dark:border-pe-rose dark:text-pe-rose-ink px-4 py-1.5 text-xs tracking-widest uppercase hover:bg-[#8E4F58] hover:text-white dark:hover:bg-pe-rose dark:hover:text-white transition-colors"
          >
            Ajuste
          </button>

          <div className="ml-auto flex items-center gap-3">
            {closingMode ? (
              <>
                <button
                  onClick={() => { setClosingMode(false); setDeclaredBalance(''); setCloseNotes(''); }}
                  className="text-xs tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 hover:text-pe-charcoal dark:hover:text-pe-beige transition-colors"
                >
                  Cancelar
                </button>
                <button
                  onClick={closeCaja}
                  disabled={operationBusy || !declaredBalance || parseFloat(declaredBalance) < 0}
                  className="bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-6 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors disabled:opacity-50"
                >
                  {operationBusy ? 'Cerrando...' : 'Cerrar caja'}
                </button>
              </>
            ) : (
              <button
                onClick={() => setClosingMode(true)}
                className="border border-[#1A1A1A] dark:border-pe-beige text-[#1A1A1A] dark:text-pe-beige px-6 py-2 text-xs tracking-widest uppercase hover:bg-[#1A1A1A] dark:hover:bg-pe-beige hover:text-[#F8F4EF] dark:hover:text-pe-black transition-colors"
              >
                Cerrar caja
              </button>
            )}
          </div>
        </div>

        {/* Close notes (shown in closing mode) */}
        {closingMode && (
          <div>
            <label htmlFor="close-notes" className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">
              Notas de cierre (opcional)
            </label>
            <textarea
              id="close-notes"
              value={closeNotes}
              onChange={(e) => setCloseNotes(e.target.value)}
              placeholder="Notas de cierre (opcional)"
              rows={2}
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79] placeholder:text-pe-muted dark:placeholder:text-pe-beige/30"
            />
          </div>
        )}

        {operationError && <p className="text-red-500 text-sm">{operationError}</p>}

        {/* Movement table */}
        <div>
          <p className="text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/40 mb-3">Movimientos</p>
          {visibleMovements.length === 0 ? (
            <p className="font-display italic text-pe-muted dark:text-pe-beige/40 text-lg py-8 text-center">
              Aún sin movimientos
            </p>
          ) : (
            <div className="border border-[#EDE3D8] dark:border-white/10 overflow-auto">
              <table className="w-full min-w-[680px] text-sm">
                <thead className="bg-[#F8F4EF] dark:bg-white/5">
                  <tr className="text-left text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50">
                    <th className="px-3 py-2">Hora</th>
                    <th className="px-3 py-2">Categoría</th>
                    <th className="px-3 py-2">Concepto</th>
                    <th className="px-3 py-2 text-right">Entrada</th>
                    <th className="px-3 py-2 text-right">Salida</th>
                    <th className="px-3 py-2">Por</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleMovements.map((movement) => (
                    <MovementRow key={movement.id} movement={movement} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    );
  }

  // ─── Render: History tab ───────────────────────────────────────────────────

  function renderHistoryTab() {
    const registers = historyPage?.content ?? [];

    return (
      <div className="space-y-5">
        <div className="border border-[#EDE3D8] dark:border-white/10 p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">Estado</label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as 'ALL' | 'OPEN' | 'CLOSED')}
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79]"
            >
              <option value="ALL">Todos</option>
              <option value="OPEN">Abierta</option>
              <option value="CLOSED">Cerrada</option>
            </select>
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">Desde</label>
            <input
              type="date"
              value={fromFilter}
              onChange={(e) => setFromFilter(e.target.value)}
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79]"
            />
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">Hasta</label>
            <input
              type="date"
              value={toFilter}
              onChange={(e) => setToFilter(e.target.value)}
              className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79]"
            />
          </div>
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50 mb-1">
              {adminScope ? 'ID cajero (opcional)' : 'Accion'}
            </label>
            {adminScope ? (
              <input
                type="text"
                value={sellerFilter}
                onChange={(e) => setSellerFilter(e.target.value)}
                placeholder="UUID cajero"
                className="w-full border border-[#EDE3D8] dark:border-white/20 bg-transparent px-3 py-2 text-sm text-pe-charcoal dark:text-pe-beige focus:outline-hidden focus:border-[#B76E79]"
              />
            ) : (
              <button
                onClick={() => void loadHistory(0)}
                className="w-full bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-4 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors"
              >
                Aplicar
              </button>
            )}
          </div>
          {adminScope && (
            <div className="md:col-span-4 flex justify-end">
              <button
                onClick={() => void loadHistory(0)}
                className="bg-[#1A1A1A] dark:bg-pe-beige text-[#F8F4EF] dark:text-pe-black px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] dark:hover:bg-[#B76E79] dark:hover:text-[#F8F4EF] transition-colors"
              >
                Aplicar filtros
              </button>
            </div>
          )}
        </div>

        {historyError && <p className="text-red-500 text-sm">{historyError}</p>}
        {historyLoading && <p className="text-pe-muted dark:text-pe-beige/50 text-sm">Cargando registros...</p>}

        <div className="border border-[#EDE3D8] dark:border-white/10 overflow-auto">
          <table className="w-full min-w-[980px] text-sm">
            <thead className="bg-[#F8F4EF] dark:bg-white/5">
              <tr className="text-left text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50">
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
                  <td className="px-3 py-4 text-pe-muted dark:text-pe-beige/50" colSpan={9}>
                    No hay registros para los filtros seleccionados.
                  </td>
                </tr>
              )}
              {registers.map((register) => (
                <tr key={register.id} className="border-t border-[#EDE3D8] dark:border-white/10">
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{formatDateTime(register.openedAt)}</td>
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{formatDateTime(register.closedAt)}</td>
                  <td className="px-3 py-2">
                    <span className={`px-2 py-0.5 text-[10px] tracking-widest uppercase rounded-sm ${
                      register.status === 'OPEN'
                        ? 'bg-green-100 text-pe-positive dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-pe-charcoal/10 text-pe-muted dark:bg-white/10 dark:text-pe-beige/70'
                    }`}>
                      {register.status === 'OPEN' ? 'Abierta' : 'Cerrada'}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{CLP(register.openingBalance)}</td>
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{CLP(register.expectedBalance)}</td>
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{CLP(register.closingBalance ?? 0)}</td>
                  <td className={`px-3 py-2 font-medium ${(register.difference ?? 0) === 0 ? 'text-[#6B7A5E]' : 'text-[#B76E79]'}`}>
                    {CLP(register.difference ?? 0)}
                  </td>
                  <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{register.movements.length}</td>
                  <td className="px-3 py-2">
                    <button
                      onClick={() => setSelectedRegister(register)}
                      className="text-xs tracking-widest uppercase text-pe-rose-ink dark:text-pe-rose-ink hover:underline"
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
          <p className="text-xs text-pe-muted dark:text-pe-beige/50">
            Total registros: {historyPage?.totalElements ?? 0}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => void loadHistory(Math.max(0, historyPageNumber - 1))}
              disabled={historyPageNumber === 0 || historyLoading}
              className="border border-[#EDE3D8] dark:border-white/20 px-3 py-1.5 text-xs tracking-widest uppercase text-pe-charcoal dark:text-pe-beige disabled:opacity-40 hover:border-[#B76E79] transition-colors"
            >
              Anterior
            </button>
            <button
              onClick={() => void loadHistory(historyPageNumber + 1)}
              disabled={historyLoading || !historyPage || historyPageNumber + 1 >= historyPage.totalPages}
              className="border border-[#EDE3D8] dark:border-white/20 px-3 py-1.5 text-xs tracking-widest uppercase text-pe-charcoal dark:text-pe-beige disabled:opacity-40 hover:border-[#B76E79] transition-colors"
            >
              Siguiente
            </button>
          </div>
        </div>

        {selectedRegister && (
          <div className="border border-[#EDE3D8] dark:border-white/10 p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm tracking-widest uppercase text-pe-muted dark:text-pe-beige/60">
                Detalle registro #{shortId(selectedRegister.id)}
              </h3>
              <span className="text-xs text-pe-muted dark:text-pe-beige/45">
                Cajero: {shortId(selectedRegister.sellerId)}
              </span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <div>
                <p className="text-pe-muted dark:text-pe-beige/45 text-xs uppercase tracking-widest">Apertura</p>
                <p className="text-pe-charcoal dark:text-pe-beige">{formatDateTime(selectedRegister.openedAt)}</p>
              </div>
              <div>
                <p className="text-pe-muted dark:text-pe-beige/45 text-xs uppercase tracking-widest">Cierre</p>
                <p className="text-pe-charcoal dark:text-pe-beige">{formatDateTime(selectedRegister.closedAt)}</p>
              </div>
              <div>
                <p className="text-pe-muted dark:text-pe-beige/45 text-xs uppercase tracking-widest">Notas</p>
                <p className="text-pe-charcoal dark:text-pe-beige">{selectedRegister.notes?.trim() || '-'}</p>
              </div>
            </div>
            <div className="overflow-auto border border-[#EDE3D8] dark:border-white/10">
              <table className="w-full min-w-[760px] text-sm">
                <thead className="bg-[#F8F4EF] dark:bg-white/5">
                  <tr className="text-left text-[10px] tracking-widest uppercase text-pe-muted dark:text-pe-beige/50">
                    <th className="px-3 py-2">Hora</th>
                    <th className="px-3 py-2">Categoría</th>
                    <th className="px-3 py-2">Concepto</th>
                    <th className="px-3 py-2 text-right">Entrada</th>
                    <th className="px-3 py-2 text-right">Salida</th>
                    <th className="px-3 py-2">Por</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedRegister.movements.length === 0 && (
                    <tr>
                      <td className="px-3 py-3 text-pe-muted dark:text-pe-beige/50" colSpan={6}>Sin movimientos.</td>
                    </tr>
                  )}
                  {selectedRegister.movements.map((movement) => (
                    <MovementRow key={movement.id} movement={movement} />
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ─── Root render ───────────────────────────────────────────────────────────

  return (
    <>
      <MovementDrawer
        open={drawerOpen}
        category={drawerCategory}
        onClose={() => setDrawerOpen(false)}
        onSubmit={createMovement}
        busy={drawerBusy}
        error={drawerError}
      />

      <div className="space-y-5">
        {/* Tab bar */}
        <div className="border-b border-[#EDE3D8] dark:border-white/10">
          <nav className="flex gap-2">
            {(['operacion', 'registros'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTabAndUrl(t)}
                className={`px-4 py-2 text-xs tracking-widest uppercase border-b-2 transition-colors ${
                  tab === t
                    ? 'border-[#B76E79] text-pe-black dark:text-pe-beige'
                    : 'border-transparent text-pe-muted dark:text-pe-beige/50 hover:text-pe-charcoal dark:hover:text-pe-beige/80'
                }`}
              >
                {t === 'operacion' ? 'Operacion' : 'Registros'}
              </button>
            ))}
          </nav>
        </div>

        {/* Tab content */}
        {tab === 'operacion' && (
          <>
            {operationView === 'loading' && (
              <p className="text-pe-muted dark:text-pe-beige/50 text-sm">Cargando...</p>
            )}
            {(operationView === 'no_caja' || operationView === 'open_form') && renderOpenForm()}
            {operationView === 'closed' && renderClosed()}
            {operationView === 'open' && renderOpenCaja()}
          </>
        )}
        {tab === 'registros' && renderHistoryTab()}
      </div>
    </>
  );
}

// ─── Movement table row (reused in both open caja and history detail) ─────────

function MovementRow({ movement }: { movement: CashMovementDto }) {
  const ts = movement.createdAt ?? movement.recordedAt;
  const isIn = movement.type === 'IN';
  return (
    <tr className="border-t border-[#EDE3D8] dark:border-white/10">
      <td className="px-3 py-2 text-pe-muted dark:text-pe-beige/60 tabular-nums">{formatTime(ts)}</td>
      <td className="px-3 py-2">
        <CategoryChip category={movement.category ?? 'ADJUSTMENT'} />
      </td>
      <td className="px-3 py-2 text-pe-charcoal dark:text-pe-beige">{movement.description}</td>
      <td className="px-3 py-2 text-right tabular-nums text-[#6B7A5E] dark:text-green-400">
        {isIn ? CLP(movement.amount) : '—'}
      </td>
      <td className="px-3 py-2 text-right tabular-nums text-[#B76E79]">
        {!isIn ? CLP(movement.amount) : '—'}
      </td>
      <td className="px-3 py-2 text-pe-muted dark:text-pe-beige/50 font-mono text-xs">
        {shortId(movement.recordedBy)}
      </td>
    </tr>
  );
}
