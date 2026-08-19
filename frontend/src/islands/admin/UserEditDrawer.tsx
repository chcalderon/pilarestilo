import { useEffect, useState } from 'react';
import { Check, ChevronDown, ChevronUp, Eye, EyeOff, X } from 'lucide-react';
import {
  deleteAdminUser,
  getCustomerCredit,
  grantCustomerCredit,
  resetAdminUserPassword,
  updateAdminUser,
  type AdminUserDto,
} from '../../lib/api';
import Overlay from './Overlay';

const WORKER_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'] as const;
type WorkerRole = (typeof WORKER_ROLES)[number];

interface Props {
  user: AdminUserDto;
  token: string;
  currentUserId: string;
  canUpdate: boolean;
  isLegacyAdmin: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

function SectionCard({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-md border border-[var(--pe-border)] bg-[var(--pe-surface-soft)] p-4 space-y-3">
      <p className="text-[10px] tracking-widest uppercase opacity-50">{label}</p>
      {children}
    </div>
  );
}

// Uses CSS vars so it adapts to both light (data-theme='light') and dark (data-theme='dark')
const inputCls =
  'w-full bg-[var(--pe-surface-card)] border border-[var(--pe-border)] rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)] placeholder:opacity-30 disabled:opacity-50';

const labelCls = 'text-[10px] tracking-widest uppercase opacity-60';

// --pe-ink = #1A1A1A light / #F8F4EF dark → auto-inverts for bg/text
const btnPrimary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs bg-[var(--pe-ink)] text-[var(--pe-surface)] hover:opacity-80 disabled:opacity-40 transition-opacity';

const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-[var(--pe-border)] hover:bg-[var(--pe-surface-soft)] disabled:opacity-40 transition-colors';

const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs border border-red-300/60 text-red-500 hover:bg-red-50/50 disabled:opacity-40 transition-colors';

function OkBadge({ show }: { show: boolean }) {
  if (!show) return null;
  return (
    <span className="text-[0.7rem] text-green-600 flex items-center gap-1">
      <Check size={12} /> Guardado
    </span>
  );
}

function flashOk(setter: (v: boolean) => void) {
  setter(true);
  setTimeout(() => setter(false), 2000);
}

export default function UserEditDrawer({
  user,
  token,
  currentUserId,
  canUpdate,
  isLegacyAdmin,
  onClose,
  onSaved,
}: Props) {
  const isSelf = user.id === currentUserId;
  const isCustomer = user.role === 'CUSTOMER';
  const canEditBasics = canUpdate && !isSelf;

  // Info
  const [fullName, setFullName] = useState(user.fullName);
  const [nameSaving, setNameSaving] = useState(false);
  const [nameError, setNameError] = useState('');
  const [nameOk, setNameOk] = useState(false);

  // Status
  const [active, setActive] = useState(user.active);
  const [statusSaving, setStatusSaving] = useState(false);
  const [statusError, setStatusError] = useState('');
  const [statusOk, setStatusOk] = useState(false);

  // Password
  const [pwOpen, setPwOpen] = useState(false);
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [pwSaving, setPwSaving] = useState(false);
  const [pwError, setPwError] = useState('');
  const [pwOk, setPwOk] = useState(false);

  // Credit
  const [creditBalance, setCreditBalance] = useState<number | null>(null);
  const [creditCurrency, setCreditCurrency] = useState('CLP');
  const [creditAmount, setCreditAmount] = useState('50000');
  const [creditReason, setCreditReason] = useState('Crédito administrativo');
  const [creditSaving, setCreditSaving] = useState(false);
  const [creditError, setCreditError] = useState('');
  const [creditOk, setCreditOk] = useState(false);

  // Worker role
  const [workerRole, setWorkerRole] = useState<WorkerRole>(
    WORKER_ROLES.includes(user.role as WorkerRole) ? (user.role as WorkerRole) : WORKER_ROLES[0],
  );
  const [vigencyStart, setVigencyStart] = useState('');
  const [vigencyEnd, setVigencyEnd] = useState('');
  const [roleSaving, setRoleSaving] = useState(false);
  const [roleError, setRoleError] = useState('');
  const [roleOk, setRoleOk] = useState(false);

  // Delete
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  // Role change
  const [roleChangeSaving, setRoleChangeSaving] = useState(false);
  const [roleChangeError, setRoleChangeError] = useState('');

  useEffect(() => {
    if (!isLegacyAdmin) return;
    getCustomerCredit(user.id, token)
      .then((c) => {
        if (!c) return;
        setCreditBalance(Number(c.balanceAmount ?? 0));
        setCreditCurrency(c.balanceCurrency ?? 'CLP');
      })
      .catch(() => {});
  }, [user.id, token]);

  const moneyFormat = (n: number) =>
    new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: creditCurrency,
      maximumFractionDigits: 0,
    }).format(n);

  async function saveName() {
    if (!canUpdate) return;
    const trimmed = fullName.trim();
    if (!trimmed || trimmed === user.fullName) return;
    setNameSaving(true);
    setNameError('');
    try {
      await updateAdminUser(user.id, { fullName: trimmed }, token);
      onSaved();
      flashOk(setNameOk);
    } catch {
      setNameError('No se pudo actualizar el nombre.');
    } finally {
      setNameSaving(false);
    }
  }

  async function saveStatus(next: boolean) {
    if (!canUpdate) return;
    setStatusSaving(true);
    setStatusError('');
    try {
      await updateAdminUser(user.id, { active: next }, token);
      setActive(next);
      onSaved();
      flashOk(setStatusOk);
    } catch {
      setStatusError('No se pudo cambiar el estado.');
    } finally {
      setStatusSaving(false);
    }
  }

  async function savePassword() {
    if (!isLegacyAdmin) return;
    if (newPw.length < 8) {
      setPwError('Mínimo 8 caracteres.');
      return;
    }
    if (newPw !== confirmPw) {
      setPwError('Las contraseñas no coinciden.');
      return;
    }
    setPwSaving(true);
    setPwError('');
    try {
      await resetAdminUserPassword(user.id, newPw, token);
      setNewPw('');
      setConfirmPw('');
      setPwOpen(false);
      flashOk(setPwOk);
    } catch {
      setPwError('No se pudo actualizar la contraseña.');
    } finally {
      setPwSaving(false);
    }
  }

  async function saveCredit() {
    if (!isLegacyAdmin) return;
    const amount = Number(creditAmount.replace(/[^\d]/g, ''));
    if (!amount || amount <= 0) {
      setCreditError('Monto inválido.');
      return;
    }
    const reason = creditReason.trim() || 'Crédito administrativo';
    setCreditSaving(true);
    setCreditError('');
    try {
      await grantCustomerCredit(user.id, amount, reason, token);
      setCreditBalance((prev) => (prev ?? 0) + amount);
      onSaved();
      flashOk(setCreditOk);
    } catch {
      setCreditError('No se pudo otorgar el crédito.');
    } finally {
      setCreditSaving(false);
    }
  }

  async function assignRole() {
    if (!isLegacyAdmin) return;
    if (!vigencyStart) {
      setRoleError('Fecha de inicio requerida.');
      return;
    }
    setRoleSaving(true);
    setRoleError('');
    try {
      const r = await fetch(`/api/admin/workers/${user.id}/assign`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: workerRole, vigencyStart, vigencyEnd: vigencyEnd || null }),
      });
      if (!r.ok) throw new Error();
      onSaved();
      flashOk(setRoleOk);
    } catch {
      setRoleError('No se pudo asignar el rol.');
    } finally {
      setRoleSaving(false);
    }
  }

  async function revokeRole() {
    if (!isLegacyAdmin) return;
    setRoleSaving(true);
    setRoleError('');
    try {
      const r = await fetch(`/api/admin/workers/${user.id}/revoke`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!r.ok) throw new Error();
      onSaved();
      flashOk(setRoleOk);
    } catch {
      setRoleError('No se pudo revocar el rol.');
    } finally {
      setRoleSaving(false);
    }
  }

  async function handleDeleteUser() {
    if (!isLegacyAdmin) return;
    setDeleteSaving(true);
    setDeleteError('');
    try {
      await deleteAdminUser(user.id, token);
      onSaved();
      onClose();
    } catch {
      setDeleteError('No se pudo eliminar. Puede tener registros asociados.');
      setDeleteConfirm(false);
    } finally {
      setDeleteSaving(false);
    }
  }

  async function changeRole() {
    if (!isLegacyAdmin) return;
    const nextRole = isCustomer ? 'SELLER' : 'CUSTOMER';
    setRoleChangeSaving(true);
    setRoleChangeError('');
    try {
      await updateAdminUser(user.id, { role: nextRole }, token);
      onSaved();
      onClose();
    } catch {
      setRoleChangeError('No se pudo cambiar el tipo de usuario.');
    } finally {
      setRoleChangeSaving(false);
    }
  }

  return (
    <Overlay>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px]"
        onClick={onClose}
      />

      {/* Drawer panel — bg uses CSS var so it adapts to data-theme */}
      <div className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-[480px] bg-[var(--pe-surface-card)] shadow-2xl overflow-y-auto">

        {/* Header */}
        <div className="flex items-start justify-between p-5 pb-4 border-b border-[var(--pe-border)] shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-[var(--pe-ink)] flex items-center justify-center text-[var(--pe-surface)] font-display text-base font-medium shrink-0">
              {initials(user.fullName)}
            </div>
            <div>
              <p className="font-display text-base leading-tight">
                {user.fullName}
              </p>
              <p className="font-sans text-[0.7rem] opacity-45 mt-0.5">
                {user.email}
              </p>
              <div className="flex items-center gap-1.5 mt-1.5">
                <span
                  className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-green-500' : 'bg-amber-500'}`}
                />
                <span className="text-[0.65rem] font-sans uppercase tracking-widest opacity-50">
                  {active ? 'Activo' : 'Bloqueado'}
                </span>
                {user.createdAt && (
                  <>
                    <span className="opacity-20">·</span>
                    <span className="text-[0.65rem] font-sans opacity-35">
                      {new Date(user.createdAt).toLocaleDateString('es-CL')}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xs opacity-40 hover:opacity-100 transition-opacity"
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-col gap-3 p-5">
          {canUpdate && !isLegacyAdmin && (
            <p className="text-[0.7rem] font-sans opacity-55 border border-[var(--pe-border)] rounded-md px-3 py-2">
              Esta sesion puede editar nombre y estado. Acciones sensibles como contrasena, rol laboral y eliminacion siguen reservadas para administracion legacy.
            </p>
          )}

          {/* Own-account guard */}
          {isSelf && (
            <p className="text-[0.7rem] font-sans opacity-55 border border-[var(--pe-border)] rounded-md px-3 py-2">
              No puedes modificar tu propia cuenta desde aquí. Usa{' '}
              <a href="/es/account?tab=profile" className="underline">
                Mi cuenta
              </a>
              .
            </p>
          )}

          {/* Información básica */}
          <SectionCard label="Información básica">
            <label className="flex flex-col gap-1.5">
              <span className={labelCls}>Nombre completo</span>
              <input
                type="text"
                value={fullName}
                onChange={(e) => {
                  setFullName(e.target.value);
                  setNameError('');
                }}
                disabled={!canEditBasics || nameSaving}
                className={inputCls}
                placeholder="Nombre del usuario"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className={labelCls}>Correo electrónico</span>
              <input
                type="email"
                value={user.email}
                readOnly
                className={`${inputCls} opacity-50 cursor-not-allowed`}
              />
            </label>
            {nameError && <p className="text-[0.7rem] text-red-500">{nameError}</p>}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={saveName}
                disabled={!canEditBasics || nameSaving || fullName.trim() === user.fullName || !fullName.trim()}
                className={btnPrimary}
              >
                {nameSaving ? 'Guardando...' : 'Guardar nombre'}
              </button>
              <OkBadge show={nameOk} />
            </div>
          </SectionCard>

          {/* Estado */}
          <SectionCard label="Estado de cuenta">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm">
                  {active ? 'Cuenta activa' : 'Cuenta bloqueada'}
                </p>
                <p className="text-[0.7rem] opacity-50 mt-0.5">
                  Bloquear impide el acceso a la cuenta
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (canEditBasics && !statusSaving) void saveStatus(!active);
                }}
                disabled={!canEditBasics || statusSaving}
                aria-label={active ? 'Bloquear usuario' : 'Habilitar usuario'}
                className={`relative w-11 h-6 rounded-full overflow-hidden transition-colors duration-200 focus:outline-hidden disabled:opacity-40 ${
                  active ? 'bg-green-500' : 'bg-[var(--pe-border)]'
                }`}
              >
                <span
                  className={`absolute top-[3px] left-[3px] w-[18px] h-[18px] rounded-full bg-white shadow-xs transition-transform duration-200 ${
                    active ? 'translate-x-[20px]' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
            {statusError && <p className="text-[0.7rem] text-red-500">{statusError}</p>}
            {statusOk && (
              <span className="text-[0.7rem] text-green-600 flex items-center gap-1">
                <Check size={12} /> Estado actualizado
              </span>
            )}
          </SectionCard>

          {/* Contraseña */}
          {isLegacyAdmin && (
          <SectionCard label="Contraseña">
            <button
              type="button"
              onClick={() => setPwOpen((v) => !v)}
              disabled={isSelf}
              className="flex items-center justify-between w-full text-sm disabled:opacity-40"
            >
              <span>Restablecer contraseña</span>
              {pwOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </button>
            {pwOpen && (
              <div className="space-y-3 pt-1">
                <label className="flex flex-col gap-1.5">
                  <span className={labelCls}>Nueva contraseña</span>
                  <div className="relative">
                    <input
                      type={showPw ? 'text' : 'password'}
                      value={newPw}
                      onChange={(e) => {
                        setNewPw(e.target.value);
                        setPwError('');
                      }}
                      className={inputCls}
                      placeholder="Mínimo 8 caracteres"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPw((v) => !v)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 opacity-40 hover:opacity-80"
                    >
                      {showPw ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                  </div>
                </label>
                <label className="flex flex-col gap-1.5">
                  <span className={labelCls}>Confirmar contraseña</span>
                  <input
                    type={showPw ? 'text' : 'password'}
                    value={confirmPw}
                    onChange={(e) => {
                      setConfirmPw(e.target.value);
                      setPwError('');
                    }}
                    className={inputCls}
                    placeholder="Repite la contraseña"
                  />
                </label>
                {pwError && <p className="text-[0.7rem] text-red-500">{pwError}</p>}
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={savePassword}
                    disabled={pwSaving}
                    className={btnPrimary}
                  >
                    {pwSaving ? 'Actualizando...' : 'Actualizar contraseña'}
                  </button>
                  <OkBadge show={pwOk} />
                </div>
              </div>
            )}
          </SectionCard>
          )}

          {/* Crédito */}
          {isLegacyAdmin && (
          <SectionCard label="Crédito">
            <div className="flex items-center justify-between">
              <span className="text-[0.7rem] opacity-50">
                Saldo actual
              </span>
              <span className="font-display">
                {creditBalance !== null ? moneyFormat(creditBalance) : '—'}
              </span>
            </div>
            <label className="flex flex-col gap-1.5">
              <span className={labelCls}>Monto a otorgar (CLP)</span>
              <input
                type="text"
                inputMode="numeric"
                value={creditAmount}
                onChange={(e) => {
                  setCreditAmount(e.target.value);
                  setCreditError('');
                }}
                className={inputCls}
                placeholder="50000"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className={labelCls}>Motivo</span>
              <input
                type="text"
                value={creditReason}
                onChange={(e) => setCreditReason(e.target.value)}
                className={inputCls}
              />
            </label>
            {creditError && <p className="text-[0.7rem] text-red-500">{creditError}</p>}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={saveCredit}
                disabled={creditSaving}
                className={btnPrimary}
              >
                {creditSaving ? 'Otorgando...' : 'Otorgar crédito'}
              </button>
              <OkBadge show={creditOk} />
            </div>
          </SectionCard>
          )}

          {/* Rol laboral — trabajadores only */}
          {isLegacyAdmin && !isCustomer && (
            <SectionCard label="Rol laboral">
              <label className="flex flex-col gap-1.5">
                <span className={labelCls}>Rol</span>
                <div className="relative">
                  <select
                    value={workerRole}
                    onChange={(e) => setWorkerRole(e.target.value as WorkerRole)}
                    disabled={isSelf || roleSaving}
                    className={`${inputCls} appearance-none pr-8 cursor-pointer`}
                  >
                    {WORKER_ROLES.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                  <ChevronDown
                    size={14}
                    className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 opacity-40"
                  />
                </div>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className={labelCls}>Inicio vigencia</span>
                <input
                  type="date"
                  value={vigencyStart}
                  onChange={(e) => setVigencyStart(e.target.value)}
                  disabled={isSelf || roleSaving}
                  className={inputCls}
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className={labelCls}>Fin vigencia (opcional)</span>
                <input
                  type="date"
                  value={vigencyEnd}
                  onChange={(e) => setVigencyEnd(e.target.value)}
                  disabled={isSelf || roleSaving}
                  className={inputCls}
                />
              </label>
              {roleError && <p className="text-[0.7rem] text-red-500">{roleError}</p>}
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={assignRole}
                  disabled={isSelf || roleSaving}
                  className={btnPrimary}
                >
                  {roleSaving ? 'Guardando...' : 'Asignar rol'}
                </button>
                <button
                  type="button"
                  onClick={revokeRole}
                  disabled={isSelf || roleSaving}
                  className={btnDanger}
                >
                  Revocar
                </button>
                <OkBadge show={roleOk} />
              </div>
            </SectionCard>
          )}

          {/* Footer — zona de riesgo */}
          {isLegacyAdmin && (
          <div className="border-t border-[var(--pe-border)] pt-4 space-y-3">
            <p className="text-[10px] tracking-widest uppercase opacity-40">
              Zona de riesgo
            </p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={changeRole}
                disabled={isSelf || roleChangeSaving}
                className={btnSecondary}
              >
                {roleChangeSaving
                  ? 'Cambiando...'
                  : isCustomer
                    ? 'Pasar a trabajador'
                    : 'Pasar a cliente'}
              </button>

              {!deleteConfirm ? (
                <button
                  type="button"
                  onClick={() => setDeleteConfirm(true)}
                  disabled={isSelf}
                  className={btnDanger}
                >
                  Eliminar usuario
                </button>
              ) : (
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[0.7rem] text-red-500">¿Confirmar eliminación?</span>
                  <button
                    type="button"
                    onClick={handleDeleteUser}
                    disabled={deleteSaving}
                    className={btnDanger}
                  >
                    {deleteSaving ? 'Eliminando...' : 'Sí, eliminar'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeleteConfirm(false)}
                    className={btnSecondary}
                  >
                    Cancelar
                  </button>
                </div>
              )}
            </div>
            {roleChangeError && (
              <p className="text-[0.7rem] text-red-500">{roleChangeError}</p>
            )}
            {deleteError && (
              <p className="text-[0.7rem] text-red-500">{deleteError}</p>
            )}
          </div>
          )}
        </div>
      </div>
    </Overlay>
  );
}
