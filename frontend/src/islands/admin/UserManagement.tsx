import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pencil, RefreshCw, ShieldCheck, ShieldOff, Trash2 } from 'lucide-react';
import UserEditDrawer from './UserEditDrawer';
import {
  getAdminOrdersByCustomer,
  getAdminUsers,
  getCustomerCredit,
  getCustomerCreditMovements,
  updateAdminUser,
  type AdminUserDto,
} from '../../lib/api';
import { readAuthTokenCookie, useAuthStore } from '../../lib/authStore';
import DataTable, { type Column } from './DataTable';

type TabKey = 'customers' | 'workers';
type UserStatusFilter = 'ALL' | 'ACTIVE' | 'BLOCKED';

type UserMetrics = {
  loading: boolean;
  creditAvailable: number;
  creditUsed: number;
  currency: string;
  paidOrders: number;
  pendingOrders: number;
};
type FeedbackState = {
  tone: 'success' | 'error';
  text: string;
};

const PAID_ORDER_STATUSES = new Set(['PAID', 'PREPARING_ORDER', 'SHIPPED', 'DELIVERED']);
const PENDING_ORDER_STATUSES = new Set(['CREATED', 'PENDING_PAYMENT', 'PAYMENT_UNDER_REVIEW']);
const USER_PAGE_SIZE = 12;
const ROLE_BY_TAB: Record<TabKey, AdminUserDto['role']> = {
  customers: 'CUSTOMER',
  workers: 'SELLER',
};

const DEFAULT_METRICS: UserMetrics = {
  loading: false,
  creditAvailable: 0,
  creditUsed: 0,
  currency: 'CLP',
  paidOrders: 0,
  pendingOrders: 0,
};

export default function UserManagement() {
  const { token, user } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tab, setTab] = useState<TabKey>('customers');
  const [customers, setCustomers] = useState<AdminUserDto[]>([]);
  const [workers, setWorkers] = useState<AdminUserDto[]>([]);
  const [customersPage, setCustomersPage] = useState(0);
  const [workersPage, setWorkersPage] = useState(0);
  const [customersTotal, setCustomersTotal] = useState(0);
  const [workersTotal, setWorkersTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<UserStatusFilter>('ALL');
  const [counters, setCounters] = useState({ customers: 0, workers: 0, blocked: 0 });
  const [metricsByUser, setMetricsByUser] = useState<Record<string, UserMetrics>>({});
  const [loading, setLoading] = useState(true);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [editingUser, setEditingUser] = useState<AdminUserDto | null>(null);

  const visibleUsers = useMemo(() => (tab === 'customers' ? customers : workers), [customers, workers, tab]);
  const visibleTotal = tab === 'customers' ? customersTotal : workersTotal;
  const visiblePage = tab === 'customers' ? customersPage : workersPage;
  const statusFilterValue = statusFilter === 'ALL' ? undefined : statusFilter === 'ACTIVE';

  const moneyFormat = useCallback((amount: number, currency = 'CLP') => {
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  }, []);

  const loadCounters = useCallback(async () => {
    if (!effectiveToken) {
      return;
    }
    const [customersRes, workersRes, blockedRes] = await Promise.all([
      getAdminUsers(effectiveToken, 0, 1, { role: 'CUSTOMER' }),
      getAdminUsers(effectiveToken, 0, 1, { role: 'SELLER' }),
      getAdminUsers(effectiveToken, 0, 1, { active: false }),
    ]);
    setCounters({
      customers: customersRes.totalElements ?? 0,
      workers: workersRes.totalElements ?? 0,
      blocked: blockedRes.totalElements ?? 0,
    });
  }, [effectiveToken]);

  const loadCurrentTab = useCallback(async () => {
    if (!effectiveToken) {
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      if (tab === 'customers') {
        const page = await getAdminUsers(effectiveToken, customersPage, USER_PAGE_SIZE, {
          role: ROLE_BY_TAB.customers,
          active: statusFilterValue,
        });
        const lastPage = Math.max((page.totalPages ?? 1) - 1, 0);
        if ((page.content ?? []).length === 0 && (page.totalElements ?? 0) > 0 && customersPage > lastPage) {
          setCustomersPage(lastPage);
          return;
        }
        setCustomers(page.content ?? []);
        setCustomersTotal(page.totalElements ?? 0);
        return;
      }

      const page = await getAdminUsers(effectiveToken, workersPage, USER_PAGE_SIZE, {
        role: ROLE_BY_TAB.workers,
        active: statusFilterValue,
      });
      const lastPage = Math.max((page.totalPages ?? 1) - 1, 0);
      if ((page.content ?? []).length === 0 && (page.totalElements ?? 0) > 0 && workersPage > lastPage) {
        setWorkersPage(lastPage);
        return;
      }
      setWorkers(page.content ?? []);
      setWorkersTotal(page.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  }, [effectiveToken, tab, customersPage, workersPage, statusFilterValue]);

  const loadMetrics = useCallback(async (u: AdminUserDto) => {
    if (!effectiveToken) return;
    setMetricsByUser((prev) => ({
      ...prev,
      [u.id]: prev[u.id] ?? { ...DEFAULT_METRICS, loading: true },
    }));

    const [ordersPage, credit, movementsPage] = await Promise.all([
      getAdminOrdersByCustomer(u.id, effectiveToken, 0, 100),
      u.role === 'CUSTOMER' ? getCustomerCredit(u.id, effectiveToken) : Promise.resolve(null),
      u.role === 'CUSTOMER' ? getCustomerCreditMovements(u.id, effectiveToken, 0, 200) : Promise.resolve({ content: [] }),
    ]);

    const paidOrders = (ordersPage.content ?? []).filter((o) => PAID_ORDER_STATUSES.has(o.status)).length;
    const pendingOrders = (ordersPage.content ?? []).filter((o) => PENDING_ORDER_STATUSES.has(o.status)).length;
    const creditUsed = (movementsPage.content ?? [])
      .filter((m) => m.type === 'CREDIT_USED')
      .reduce((acc, row) => acc + Number(row.amount ?? 0), 0);

    setMetricsByUser((prev) => ({
      ...prev,
      [u.id]: {
        loading: false,
        creditAvailable: Number(credit?.balanceAmount ?? 0),
        creditUsed,
        currency: credit?.balanceCurrency ?? 'CLP',
        paidOrders,
        pendingOrders,
      },
    }));
  }, [effectiveToken]);

  useEffect(() => {
    void loadCounters();
  }, [loadCounters]);

  useEffect(() => {
    void loadCurrentTab();
  }, [loadCurrentTab]);

  useEffect(() => {
    const missing = visibleUsers.filter((u) => !metricsByUser[u.id]).slice(0, 20);
    for (const u of missing) {
      void loadMetrics(u);
    }
  }, [visibleUsers, metricsByUser, loadMetrics]);

  const refreshData = useCallback(async () => {
    await Promise.all([loadCounters(), loadCurrentTab()]);
  }, [loadCounters, loadCurrentTab]);

  async function handleToggleActive(target: AdminUserDto) {
    if (!effectiveToken || busyUserId) return;
    setBusyUserId(target.id);
    try {
      const updated = await updateAdminUser(target.id, { active: !target.active }, effectiveToken);
      await refreshData();
      setFeedback({
        tone: 'success',
        text: updated.active ? 'Usuario habilitado correctamente.' : 'Usuario bloqueado correctamente.',
      });
    } catch {
      setFeedback({ tone: 'error', text: 'No se pudo actualizar el estado del usuario.' });
    } finally {
      setBusyUserId(null);
    }
  }

  const customerColumns: Column<AdminUserDto>[] = [
    {
      key: 'email',
      header: 'Cliente',
      width: '220px',
      render: (row) => (
        <div className="flex flex-col">
          <span className="font-sans text-[0.78rem] text-pe-charcoal">{row.fullName}</span>
          <span className="font-sans text-[0.68rem] text-pe-charcoal/45">{row.email}</span>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Estado',
      width: '110px',
      render: (row) => (
        <span className={['font-sans text-[0.65rem] uppercase tracking-wider px-2 py-1',
          row.active ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'].join(' ')}
        >
          {row.active ? 'Habilitado' : 'Bloqueado'}
        </span>
      ),
    },
    {
      key: 'creditAvailable',
      header: 'Credito',
      width: '100px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">Cargando...</span>;
        return <span className="font-sans text-[0.74rem] text-pe-charcoal">{moneyFormat(m.creditAvailable, m.currency)}</span>;
      },
    },
    {
      key: 'creditUsed',
      header: 'Uso',
      width: '90px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">-</span>;
        return <span className="font-sans text-[0.74rem] text-pe-charcoal/65">{moneyFormat(m.creditUsed, m.currency)}</span>;
      },
    },
    {
      key: 'payments',
      header: 'Pagos',
      width: '130px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">-</span>;
        return (
          <span className="font-sans text-[0.72rem] text-pe-charcoal/65">
            {m.paidOrders} pagados / {m.pendingOrders} pendientes
          </span>
        );
      },
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '150px',
      render: (row) => (
        <div className="flex flex-wrap gap-1.5">
          <button
            type="button"
            title="Editar usuario"
            onClick={(e) => { e.stopPropagation(); setEditingUser(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.66rem] font-sans uppercase tracking-wider rounded-sm border border-pe-black/12 text-pe-charcoal hover:border-pe-black/30 hover:bg-pe-black/[0.03] disabled:opacity-45 transition-all"
          >
            <Pencil size={13} /> Editar
          </button>
          <button
            type="button"
            title={row.active ? 'Bloquear usuario' : 'Habilitar usuario'}
            onClick={(e) => { e.stopPropagation(); void handleToggleActive(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center justify-center p-2 rounded-sm text-pe-charcoal/60 hover:text-pe-charcoal hover:bg-pe-black/[0.04] disabled:opacity-45 transition-all"
          >
            {row.active ? <ShieldOff size={15} /> : <ShieldCheck size={15} />}
          </button>
          <button
            type="button"
            title="Eliminar usuario"
            onClick={(e) => { e.stopPropagation(); setEditingUser(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center justify-center p-2 rounded-sm text-red-400 hover:text-red-600 hover:bg-red-50/60 disabled:opacity-45 transition-all"
          >
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ];

  const workerColumns: Column<AdminUserDto>[] = [
    {
      key: 'email',
      header: 'Trabajador',
      width: '220px',
      render: (row) => (
        <div className="flex flex-col">
          <span className="font-sans text-[0.78rem] text-pe-charcoal">{row.fullName}</span>
          <span className="font-sans text-[0.68rem] text-pe-charcoal/45">{row.email}</span>
        </div>
      ),
    },
    {
      key: 'benefit',
      header: 'Beneficio',
      width: '140px',
      render: () => <span className="font-sans text-[0.72rem] text-pe-rose-deep">Descuento compra 10%</span>,
    },
    {
      key: 'status',
      header: 'Estado',
      width: '110px',
      render: (row) => (
        <span className={['font-sans text-[0.65rem] uppercase tracking-wider px-2 py-1',
          row.active ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'].join(' ')}
        >
          {row.active ? 'Habilitado' : 'Bloqueado'}
        </span>
      ),
    },
    {
      key: 'payments',
      header: 'Pagos',
      width: '130px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">-</span>;
        return (
          <span className="font-sans text-[0.72rem] text-pe-charcoal/65">
            {m.paidOrders} pagados / {m.pendingOrders} pendientes
          </span>
        );
      },
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '150px',
      render: (row) => (
        <div className="flex flex-wrap gap-1.5">
          <button
            type="button"
            title="Editar trabajador"
            onClick={(e) => { e.stopPropagation(); setEditingUser(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.66rem] font-sans uppercase tracking-wider rounded-sm border border-pe-black/12 text-pe-charcoal hover:border-pe-black/30 hover:bg-pe-black/[0.03] disabled:opacity-45 transition-all"
          >
            <Pencil size={13} /> Editar
          </button>
          <button
            type="button"
            title={row.active ? 'Bloquear trabajador' : 'Habilitar trabajador'}
            onClick={(e) => { e.stopPropagation(); void handleToggleActive(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center justify-center p-2 rounded-sm text-pe-charcoal/60 hover:text-pe-charcoal hover:bg-pe-black/[0.04] disabled:opacity-45 transition-all"
          >
            {row.active ? <ShieldOff size={15} /> : <ShieldCheck size={15} />}
          </button>
          <button
            type="button"
            title="Eliminar trabajador"
            onClick={(e) => { e.stopPropagation(); setEditingUser(row); }}
            disabled={busyUserId !== null}
            className="inline-flex items-center justify-center p-2 rounded-sm text-red-400 hover:text-red-600 hover:bg-red-50/60 disabled:opacity-45 transition-all"
          >
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ];

  function handlePageChange(nextPage: number) {
    if (tab === 'customers') {
      setCustomersPage(Math.max(0, nextPage));
      return;
    }
    setWorkersPage(Math.max(0, nextPage));
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <article className="bg-[var(--pe-surface-card)] border border-[var(--pe-border)] p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-[0.2em] text-pe-charcoal/45">Clientes</p>
          <p className="font-display text-pe-black text-2xl font-light mt-1">{counters.customers}</p>
        </article>
        <article className="bg-[var(--pe-surface-card)] border border-[var(--pe-border)] p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-[0.2em] text-pe-charcoal/45">Trabajadores</p>
          <p className="font-display text-pe-black text-2xl font-light mt-1">{counters.workers}</p>
        </article>
        <article className="bg-[var(--pe-surface-card)] border border-[var(--pe-border)] p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-[0.2em] text-pe-charcoal/45">Usuarios bloqueados</p>
          <p className="font-display text-pe-black text-2xl font-light mt-1">{counters.blocked}</p>
        </article>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          <div className="inline-flex items-center p-1 bg-pe-cream border border-pe-black/10">
            <button
              type="button"
              onClick={() => setTab('customers')}
              className={[
                'px-3 py-1.5 font-sans text-[0.7rem] tracking-wider uppercase transition-colors',
                tab === 'customers' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
              ].join(' ')}
            >
              Clientes
            </button>
            <button
              type="button"
              onClick={() => setTab('workers')}
              className={[
                'px-3 py-1.5 font-sans text-[0.7rem] tracking-wider uppercase transition-colors',
                tab === 'workers' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
              ].join(' ')}
            >
              Trabajadores
            </button>
          </div>

          <select
            value={statusFilter}
            onChange={(e) => {
              const value = e.target.value as UserStatusFilter;
              setStatusFilter(value);
              setCustomersPage(0);
              setWorkersPage(0);
            }}
            className="h-[34px] w-full sm:w-auto border border-pe-black/12 bg-pe-white px-3 font-sans text-[0.72rem] uppercase tracking-[0.12em] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
          >
            <option value="ALL">Todos</option>
            <option value="ACTIVE">Habilitados</option>
            <option value="BLOCKED">Bloqueados</option>
          </select>
        </div>

        <button
          type="button"
          onClick={() => {
            setMetricsByUser({});
            void refreshData();
          }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-1 font-sans text-[0.72rem] uppercase tracking-wider text-pe-charcoal/45 hover:text-pe-rose transition-colors"
        >
          <RefreshCw size={13} /> Actualizar
        </button>
      </div>

      {feedback && (
        <div
          className={[
            'flex items-start justify-between gap-3 border px-3 py-2',
            feedback.tone === 'error'
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-green-200 bg-green-50 text-green-700',
          ].join(' ')}
        >
          <p className="font-sans text-[0.74rem]">{feedback.text}</p>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            className="font-sans text-[0.68rem] uppercase tracking-[0.16em] opacity-70 hover:opacity-100"
          >
            Cerrar
          </button>
        </div>
      )}

      <DataTable
        columns={tab === 'customers' ? customerColumns : workerColumns}
        data={visibleUsers}
        keyField="id"
        loading={loading}
        emptyMessage={tab === 'customers' ? 'No hay clientes para mostrar.' : 'No hay trabajadores para mostrar.'}
        page={visiblePage}
        pageSize={USER_PAGE_SIZE}
        total={visibleTotal}
        onPageChange={handlePageChange}
      />

      {user?.role === 'ADMIN' && (
        <div className="space-y-2">
          <p className="font-sans text-[0.72rem] text-pe-charcoal/45">
            El descuento trabajador (10%) se aplica automaticamente al checkout para usuarios con rol de trabajador.
          </p>
          <p className="font-sans text-[0.72rem] text-pe-charcoal/45">
            Para editar tu usuario admin (nombre y contrasena), usa{' '}
            <a href="/es/account?tab=profile" className="text-pe-rose-deep underline underline-offset-2 hover:text-pe-rose">
              Mi cuenta
            </a>.
          </p>
        </div>
      )}

      {editingUser && effectiveToken && (
        <UserEditDrawer
          user={editingUser}
          token={effectiveToken}
          currentUserId={user?.id ?? ''}
          onClose={() => setEditingUser(null)}
          onSaved={() => {
            setMetricsByUser((prev) => {
              const next = { ...prev };
              delete next[editingUser.id];
              return next;
            });
            void refreshData();
          }}
        />
      )}
    </div>
  );
}
