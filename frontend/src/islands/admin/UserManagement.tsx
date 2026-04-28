import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { RefreshCw, ShieldCheck, ShieldOff, Trash2, Wallet, Repeat, Pencil, KeyRound } from 'lucide-react';
import WorkerAssignmentModal from './WorkerAssignmentModal';
import {
  deleteAdminUser,
  getAdminOrdersByCustomer,
  getAdminUsers,
  getCustomerCredit,
  getCustomerCreditMovements,
  grantCustomerCredit,
  resetAdminUserPassword,
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

type UserModalState =
  | { type: 'editName'; user: AdminUserDto; fullName: string }
  | { type: 'resetPassword'; user: AdminUserDto; newPassword: string; confirmPassword: string }
  | { type: 'deleteUser'; user: AdminUserDto }
  | { type: 'grantCredit'; user: AdminUserDto; amount: string; reason: string };

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

type ActionModalProps = {
  open: boolean;
  title: string;
  confirmLabel: string;
  confirmTone?: 'default' | 'danger';
  confirmDisabled?: boolean;
  disableClose?: boolean;
  helperText?: string | null;
  onCancel: () => void;
  onConfirm: () => void;
  children: ReactNode;
};

function ActionModal({
  open,
  title,
  confirmLabel,
  confirmTone = 'default',
  confirmDisabled = false,
  disableClose = false,
  helperText,
  onCancel,
  onConfirm,
  children,
}: ActionModalProps) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-pe-black/45 px-4 py-6"
      role="dialog"
      aria-modal="true"
      onClick={() => {
        if (!disableClose) onCancel();
      }}
    >
      <div
        className="w-full max-w-xl border border-pe-black/15 bg-pe-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-pe-black/10 px-5 py-4">
          <h3 className="font-display text-xl text-pe-black">{title}</h3>
          <button
            type="button"
            onClick={onCancel}
            disabled={disableClose}
            className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55 hover:text-pe-charcoal disabled:opacity-40"
          >
            Cerrar
          </button>
        </div>

        <div className="space-y-4 px-5 py-4">
          {children}
          {helperText && (
            <p className="font-sans text-[0.72rem] text-red-600">{helperText}</p>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-pe-black/10 px-5 py-4">
          <button
            type="button"
            onClick={onCancel}
            disabled={disableClose}
            className="border border-pe-black/15 px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/65 hover:bg-pe-cream disabled:opacity-40"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={confirmDisabled}
            className={[
              'px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-offwhite disabled:opacity-45',
              confirmTone === 'danger' ? 'bg-red-600 hover:bg-red-700' : 'bg-pe-black hover:bg-pe-charcoal',
            ].join(' ')}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

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
  const [modal, setModal] = useState<UserModalState | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [assigningWorker, setAssigningWorker] = useState<{ userId: string; fullName: string; role: string } | null>(null);

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

  function closeModal() {
    if (busyUserId) return;
    setModal(null);
    setModalError(null);
  }

  function openEditUserModal(target: AdminUserDto) {
    setModal({
      type: 'editName',
      user: target,
      fullName: target.fullName,
    });
    setModalError(null);
    setFeedback(null);
  }

  function openResetPasswordModal(target: AdminUserDto) {
    setModal({
      type: 'resetPassword',
      user: target,
      newPassword: '',
      confirmPassword: '',
    });
    setModalError(null);
    setFeedback(null);
  }

  function openDeleteUserModal(target: AdminUserDto) {
    setModal({
      type: 'deleteUser',
      user: target,
    });
    setModalError(null);
    setFeedback(null);
  }

  function openGrantCreditModal(target: AdminUserDto) {
    setModal({
      type: 'grantCredit',
      user: target,
      amount: '50000',
      reason: 'Credito administrativo',
    });
    setModalError(null);
    setFeedback(null);
  }

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

  async function handleToggleRole(target: AdminUserDto) {
    if (!effectiveToken || busyUserId) return;
    const nextRole = target.role === 'CUSTOMER' ? 'SELLER' : 'CUSTOMER';
    setBusyUserId(target.id);
    try {
      const updated = await updateAdminUser(target.id, { role: nextRole }, effectiveToken);
      setMetricsByUser((prev) => {
        const next = { ...prev };
        delete next[target.id];
        return next;
      });
      await refreshData();
      setFeedback({
        tone: 'success',
        text: updated.role === 'SELLER' ? 'Usuario movido a trabajadores.' : 'Usuario movido a clientes.',
      });
    } catch {
      setFeedback({ tone: 'error', text: 'No se pudo cambiar el rol del usuario.' });
    } finally {
      setBusyUserId(null);
    }
  }

  async function submitEditUser() {
    if (!effectiveToken || !modal || modal.type !== 'editName' || busyUserId) return;
    const trimmedName = modal.fullName.trim();
    if (!trimmedName) {
      setModalError('Ingresa un nombre valido.');
      return;
    }
    if (trimmedName === modal.user.fullName) {
      closeModal();
      return;
    }

    setBusyUserId(modal.user.id);
    try {
      await updateAdminUser(modal.user.id, { fullName: trimmedName }, effectiveToken);
      await refreshData();
      setFeedback({ tone: 'success', text: 'Nombre actualizado correctamente.' });
      setModal(null);
      setModalError(null);
    } catch {
      setModalError('No se pudo actualizar el nombre del usuario.');
    } finally {
      setBusyUserId(null);
    }
  }

  async function submitResetPassword() {
    if (!effectiveToken || !modal || modal.type !== 'resetPassword' || busyUserId) return;

    if (modal.newPassword.length < 8) {
      setModalError('La nueva contrasena debe tener al menos 8 caracteres.');
      return;
    }

    if (modal.newPassword !== modal.confirmPassword) {
      setModalError('Las contrasenas no coinciden.');
      return;
    }

    setBusyUserId(modal.user.id);
    try {
      await resetAdminUserPassword(modal.user.id, modal.newPassword, effectiveToken);
      setFeedback({ tone: 'success', text: 'Contrasena actualizada correctamente.' });
      setModal(null);
      setModalError(null);
    } catch {
      setModalError('No se pudo resetear la contrasena.');
    } finally {
      setBusyUserId(null);
    }
  }

  async function submitDeleteUser() {
    if (!effectiveToken || !modal || modal.type !== 'deleteUser' || busyUserId) return;

    setBusyUserId(modal.user.id);
    try {
      await deleteAdminUser(modal.user.id, effectiveToken);
      setMetricsByUser((prev) => {
        const next = { ...prev };
        delete next[modal.user.id];
        return next;
      });
      await refreshData();
      setFeedback({ tone: 'success', text: 'Usuario eliminado correctamente.' });
      setModal(null);
      setModalError(null);
    } catch {
      setModalError('No se pudo eliminar el usuario. Puede tener registros asociados.');
    } finally {
      setBusyUserId(null);
    }
  }

  async function submitGrantCredit() {
    if (!effectiveToken || !modal || modal.type !== 'grantCredit' || busyUserId) return;

    const amount = Number(modal.amount.replace(/[^\d]/g, ''));
    if (!Number.isFinite(amount) || amount <= 0) {
      setModalError('Monto invalido.');
      return;
    }

    const reason = modal.reason.trim() || 'Credito administrativo';

    setBusyUserId(modal.user.id);
    try {
      await grantCustomerCredit(modal.user.id, amount, reason, effectiveToken);
      await loadMetrics(modal.user);
      setFeedback({ tone: 'success', text: 'Credito otorgado correctamente.' });
      setModal(null);
      setModalError(null);
    } catch {
      setModalError('No se pudo otorgar el credito.');
    } finally {
      setBusyUserId(null);
    }
  }

  const customerColumns: Column<AdminUserDto>[] = [
    {
      key: 'email',
      header: 'Cliente',
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
      width: '130px',
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
      width: '130px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">Cargando...</span>;
        return <span className="font-sans text-[0.74rem] text-pe-charcoal">{moneyFormat(m.creditAvailable, m.currency)}</span>;
      },
    },
    {
      key: 'creditUsed',
      header: 'Uso',
      width: '120px',
      render: (row) => {
        const m = metricsByUser[row.id];
        if (!m || m.loading) return <span className="text-pe-charcoal/35 text-[0.72rem]">-</span>;
        return <span className="font-sans text-[0.74rem] text-pe-charcoal/65">{moneyFormat(m.creditUsed, m.currency)}</span>;
      },
    },
    {
      key: 'payments',
      header: 'Pagos',
      width: '140px',
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
      width: '420px',
      render: (row) => (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openEditUserModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <Pencil size={11} /> Editar
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openGrantCreditModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider bg-pe-black text-pe-offwhite hover:bg-pe-charcoal disabled:opacity-50"
          >
            <Wallet size={11} /> Dar credito
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openResetPasswordModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <KeyRound size={11} /> Reset pass
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setAssigningWorker({ userId: row.id, fullName: row.fullName, role: row.role });
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            Rol
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              void handleToggleRole(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <Repeat size={11} /> Pasar a trabajador
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              void handleToggleActive(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            {row.active ? <ShieldOff size={11} /> : <ShieldCheck size={11} />}
            {row.active ? 'Bloquear' : 'Habilitar'}
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openDeleteUserModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-red-300 text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            <Trash2 size={11} /> Eliminar
          </button>
        </div>
      ),
    },
  ];

  const workerColumns: Column<AdminUserDto>[] = [
    {
      key: 'email',
      header: 'Trabajador',
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
      width: '160px',
      render: () => <span className="font-sans text-[0.72rem] text-pe-rose-deep">Descuento compra 10%</span>,
    },
    {
      key: 'status',
      header: 'Estado',
      width: '130px',
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
      width: '140px',
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
      width: '390px',
      render: (row) => (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openEditUserModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <Pencil size={11} /> Editar
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openResetPasswordModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <KeyRound size={11} /> Reset pass
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setAssigningWorker({ userId: row.id, fullName: row.fullName, role: row.role });
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            Rol
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              void handleToggleRole(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            <Repeat size={11} /> Pasar a cliente
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              void handleToggleActive(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-pe-black/20 text-pe-charcoal hover:bg-pe-cream disabled:opacity-50"
          >
            {row.active ? <ShieldOff size={11} /> : <ShieldCheck size={11} />}
            {row.active ? 'Bloquear' : 'Habilitar'}
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openDeleteUserModal(row);
            }}
            disabled={busyUserId !== null}
            className="inline-flex items-center gap-1 px-2 py-1 text-[0.65rem] font-sans uppercase tracking-wider border border-red-300 text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            <Trash2 size={11} /> Eliminar
          </button>
        </div>
      ),
    },
  ];

  const modalBusy = modal ? busyUserId === modal.user.id : false;

  const modalTitle = !modal
    ? ''
    : modal.type === 'editName'
      ? 'Editar usuario'
      : modal.type === 'resetPassword'
        ? 'Resetear contrasena'
        : modal.type === 'deleteUser'
          ? 'Eliminar usuario'
          : 'Otorgar credito';

  const modalConfirmLabel = !modal
    ? 'Guardar'
    : modal.type === 'editName'
      ? 'Guardar cambios'
      : modal.type === 'resetPassword'
        ? 'Actualizar contrasena'
        : modal.type === 'deleteUser'
          ? 'Eliminar usuario'
          : 'Otorgar credito';

  const modalConfirmTone: 'default' | 'danger' = modal?.type === 'deleteUser' ? 'danger' : 'default';

  const modalBody = !modal
    ? null
    : modal.type === 'editName'
      ? (
        <label className="flex flex-col gap-1">
          <span className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nombre completo</span>
          <input
            type="text"
            value={modal.fullName}
            onChange={(e) => {
              setModal((prev) => prev && prev.type === 'editName'
                ? { ...prev, fullName: e.target.value }
                : prev);
              setModalError(null);
            }}
            className="border border-pe-black/15 px-3 py-2 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
            placeholder="Nombre del usuario"
          />
        </label>
      )
      : modal.type === 'resetPassword'
        ? (
          <div className="space-y-3">
            <p className="font-sans text-[0.74rem] text-pe-charcoal/70">
              Usuario: <span className="font-semibold text-pe-charcoal">{modal.user.email}</span>
            </p>
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nueva contrasena</span>
              <input
                type="password"
                value={modal.newPassword}
                onChange={(e) => {
                  setModal((prev) => prev && prev.type === 'resetPassword'
                    ? { ...prev, newPassword: e.target.value }
                    : prev);
                  setModalError(null);
                }}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Minimo 8 caracteres"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Confirmar contrasena</span>
              <input
                type="password"
                value={modal.confirmPassword}
                onChange={(e) => {
                  setModal((prev) => prev && prev.type === 'resetPassword'
                    ? { ...prev, confirmPassword: e.target.value }
                    : prev);
                  setModalError(null);
                }}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Repite la contrasena"
              />
            </label>
          </div>
        )
        : modal.type === 'deleteUser'
          ? (
            <div className="space-y-2">
              <p className="font-sans text-[0.76rem] text-pe-charcoal/75">
                Esta accion no se puede deshacer.
              </p>
              <p className="font-sans text-[0.76rem] text-pe-charcoal/75">
                Usuario: <span className="font-semibold text-pe-charcoal">{modal.user.email}</span>
              </p>
            </div>
          )
          : (
            <div className="space-y-3">
              <p className="font-sans text-[0.74rem] text-pe-charcoal/70">
                Cliente: <span className="font-semibold text-pe-charcoal">{modal.user.email}</span>
              </p>
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Monto (CLP)</span>
                <input
                  type="text"
                  inputMode="numeric"
                  value={modal.amount}
                  onChange={(e) => {
                    setModal((prev) => prev && prev.type === 'grantCredit'
                      ? { ...prev, amount: e.target.value }
                      : prev);
                    setModalError(null);
                  }}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="50000"
                />
              </label>
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Motivo</span>
                <input
                  type="text"
                  value={modal.reason}
                  onChange={(e) => {
                    setModal((prev) => prev && prev.type === 'grantCredit'
                      ? { ...prev, reason: e.target.value }
                      : prev);
                    setModalError(null);
                  }}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="Credito administrativo"
                />
              </label>
            </div>
          );

  function handleModalConfirm() {
    if (!modal) return;
    if (modal.type === 'editName') {
      void submitEditUser();
      return;
    }
    if (modal.type === 'resetPassword') {
      void submitResetPassword();
      return;
    }
    if (modal.type === 'deleteUser') {
      void submitDeleteUser();
      return;
    }
    void submitGrantCredit();
  }

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
        <article className="bg-pe-white border border-pe-black/8 p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-[0.2em] text-pe-charcoal/45">Clientes</p>
          <p className="font-display text-pe-black text-2xl font-light mt-1">{counters.customers}</p>
        </article>
        <article className="bg-pe-white border border-pe-black/8 p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-[0.2em] text-pe-charcoal/45">Trabajadores</p>
          <p className="font-display text-pe-black text-2xl font-light mt-1">{counters.workers}</p>
        </article>
        <article className="bg-pe-white border border-pe-black/8 p-4">
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

      <ActionModal
        open={modal !== null}
        title={modalTitle}
        confirmLabel={modalConfirmLabel}
        confirmTone={modalConfirmTone}
        confirmDisabled={modalBusy}
        disableClose={modalBusy}
        helperText={modalError}
        onCancel={closeModal}
        onConfirm={handleModalConfirm}
      >
        {modalBody}
      </ActionModal>
    </div>
  );
}

