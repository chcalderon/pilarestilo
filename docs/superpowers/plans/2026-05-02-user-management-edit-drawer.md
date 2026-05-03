# User Management Edit Drawer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace broken multi-button row UX in `/admin/usuarios` with a consolidated slide-out drawer that fixes the broken "Rol" button, removes it from clientes, and consolidates all editing (name, status, password, credit, worker role) into one panel.

**Architecture:** New `UserEditDrawer.tsx` component handles all user editing; `UserManagement.tsx` is stripped of its ActionModal cases (edit/password/credit) and `assigningWorker` state, table rows reduced to 3–4 compact icon buttons that open the drawer.

**Tech Stack:** React 18, TypeScript, Tailwind CSS, Lucide React icons, existing `../../lib/api` functions, existing `/api/admin/workers` endpoints via `fetch`.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `frontend/src/islands/admin/UserEditDrawer.tsx` | **Create** | Slide-out panel with all edit sections |
| `frontend/src/islands/admin/UserManagement.tsx` | **Modify** | Wire drawer, simplify column buttons, remove broken code |
| `frontend/src/islands/admin/WorkerAssignmentModal.tsx` | **No change** | Left in place, just no longer imported/used |

---

## Task 1: Create `UserEditDrawer.tsx`

**Files:**
- Create: `frontend/src/islands/admin/UserEditDrawer.tsx`

- [ ] **Step 1.1: Create the file with full implementation**

```tsx
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

const WORKER_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'] as const;
type WorkerRole = (typeof WORKER_ROLES)[number];

interface Props {
  user: AdminUserDto;
  token: string;
  currentUserId: string;
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
    <div className="rounded-md border border-pe-black/8 dark:border-white/8 bg-white/60 dark:bg-white/5 p-4 space-y-3">
      <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/50 dark:text-white/40">{label}</p>
      {children}
    </div>
  );
}

const inputCls =
  'w-full bg-transparent border border-pe-black/12 dark:border-white/12 rounded-sm px-3 py-2 text-sm text-pe-charcoal dark:text-white/90 outline-none focus:ring-1 focus:ring-pe-black/20 dark:focus:ring-white/20 placeholder:text-pe-charcoal/30 disabled:opacity-50';

const labelCls =
  'text-[10px] tracking-widest uppercase text-pe-charcoal/50 dark:text-white/40';

const btnPrimary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm bg-pe-black text-pe-cream dark:bg-pe-cream dark:text-pe-black hover:bg-pe-charcoal dark:hover:bg-white/80 disabled:opacity-40 transition-colors';

const btnSecondary =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm border border-pe-black/15 dark:border-white/15 text-pe-charcoal dark:text-white/80 hover:border-pe-black/30 hover:bg-pe-black/[0.03] disabled:opacity-40 transition-colors';

const btnDanger =
  'inline-flex items-center gap-1.5 px-3 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-sm border border-red-200/60 text-red-500 hover:bg-red-50/50 disabled:opacity-40 transition-colors';

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

export default function UserEditDrawer({ user, token, currentUserId, onClose, onSaved }: Props) {
  const isSelf = user.id === currentUserId;
  const isCustomer = user.role === 'CUSTOMER';

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

  // Credit (customers only)
  const [creditBalance, setCreditBalance] = useState<number | null>(null);
  const [creditCurrency, setCreditCurrency] = useState('CLP');
  const [creditAmount, setCreditAmount] = useState('50000');
  const [creditReason, setCreditReason] = useState('Crédito administrativo');
  const [creditSaving, setCreditSaving] = useState(false);
  const [creditError, setCreditError] = useState('');
  const [creditOk, setCreditOk] = useState(false);

  // Worker role (workers only)
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
    if (!isCustomer) return;
    getCustomerCredit(user.id, token)
      .then((c) => {
        setCreditBalance(Number(c.balanceAmount ?? 0));
        setCreditCurrency(c.balanceCurrency ?? 'CLP');
      })
      .catch(() => {});
  }, [user.id, token, isCustomer]);

  const moneyFormat = (n: number) =>
    new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: creditCurrency,
      maximumFractionDigits: 0,
    }).format(n);

  async function saveName() {
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
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px]"
        onClick={onClose}
      />

      {/* Drawer panel */}
      <div className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-[480px] bg-[#FDFAF7] dark:bg-[#141414] shadow-2xl overflow-y-auto">

        {/* Header */}
        <div className="flex items-start justify-between p-5 pb-4 border-b border-pe-black/6 dark:border-white/6 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-pe-black dark:bg-pe-cream flex items-center justify-center text-pe-cream dark:text-pe-black font-display text-base font-medium shrink-0">
              {initials(user.fullName)}
            </div>
            <div>
              <p className="font-display text-pe-black dark:text-white/90 text-base leading-tight">
                {user.fullName}
              </p>
              <p className="font-sans text-[0.7rem] text-pe-charcoal/50 dark:text-white/40 mt-0.5">
                {user.email}
              </p>
              <div className="flex items-center gap-1.5 mt-1.5">
                <span
                  className={`w-1.5 h-1.5 rounded-full ${active ? 'bg-green-500' : 'bg-amber-500'}`}
                />
                <span className="text-[0.65rem] font-sans uppercase tracking-widest text-pe-charcoal/50 dark:text-white/40">
                  {active ? 'Activo' : 'Bloqueado'}
                </span>
                {user.createdAt && (
                  <>
                    <span className="text-pe-charcoal/20 dark:text-white/20">·</span>
                    <span className="text-[0.65rem] font-sans text-pe-charcoal/40 dark:text-white/30">
                      {new Date(user.createdAt).toLocaleDateString('es-CL')}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-sm text-pe-charcoal/40 hover:text-pe-black dark:hover:text-white transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-col gap-3 p-5">

          {/* Own-account guard */}
          {isSelf && (
            <p className="text-[0.7rem] font-sans text-pe-charcoal/50 dark:text-white/40 border border-pe-black/8 dark:border-white/8 rounded-md px-3 py-2">
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
                disabled={isSelf || nameSaving}
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
                disabled={isSelf || nameSaving || fullName.trim() === user.fullName || !fullName.trim()}
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
                <p className="text-sm text-pe-charcoal dark:text-white/80">
                  {active ? 'Cuenta activa' : 'Cuenta bloqueada'}
                </p>
                <p className="text-[0.7rem] text-pe-charcoal/50 dark:text-white/40 mt-0.5">
                  Bloquear impide el acceso a la cuenta
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (!isSelf && !statusSaving) void saveStatus(!active);
                }}
                disabled={isSelf || statusSaving}
                aria-label={active ? 'Bloquear usuario' : 'Habilitar usuario'}
                className={`relative w-10 h-5 rounded-full transition-colors duration-200 focus:outline-none disabled:opacity-40 ${
                  active ? 'bg-green-500' : 'bg-pe-black/20 dark:bg-white/20'
                }`}
              >
                <span
                  className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform duration-200 ${
                    active ? 'translate-x-5' : 'translate-x-0.5'
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
          <SectionCard label="Contraseña">
            <button
              type="button"
              onClick={() => setPwOpen((v) => !v)}
              disabled={isSelf}
              className="flex items-center justify-between w-full text-sm text-pe-charcoal dark:text-white/80 disabled:opacity-40"
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
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-pe-charcoal/40 hover:text-pe-charcoal"
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

          {/* Crédito — clientes only */}
          {isCustomer && (
            <SectionCard label="Crédito">
              <div className="flex items-center justify-between">
                <span className="text-[0.7rem] text-pe-charcoal/50 dark:text-white/40">
                  Saldo actual
                </span>
                <span className="font-display text-pe-black dark:text-white/90">
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
          {!isCustomer && (
            <SectionCard label="Rol laboral">
              <label className="flex flex-col gap-1.5">
                <span className={labelCls}>Rol</span>
                <select
                  value={workerRole}
                  onChange={(e) => setWorkerRole(e.target.value as WorkerRole)}
                  disabled={isSelf || roleSaving}
                  className={inputCls}
                >
                  {WORKER_ROLES.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
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
          <div className="border-t border-pe-black/6 dark:border-white/6 pt-4 space-y-3">
            <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 dark:text-white/30">
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
        </div>
      </div>
    </>
  );
}
```

- [ ] **Step 1.2: Verify file created**

```bash
ls frontend/src/islands/admin/UserEditDrawer.tsx
```
Expected: file exists, no errors.

- [ ] **Step 1.3: Commit**

```bash
git add frontend/src/islands/admin/UserEditDrawer.tsx
git commit -m "feat(admin): add UserEditDrawer slide-out panel"
```

---

## Task 2: Update `UserManagement.tsx` — imports and types

**Files:**
- Modify: `frontend/src/islands/admin/UserManagement.tsx:1-16` (imports)
- Modify: `frontend/src/islands/admin/UserManagement.tsx:34-38` (UserModalState type)

- [ ] **Step 2.1: Replace import block (lines 1–16)**

Replace the entire top-of-file import section:

```tsx
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { Pencil, RefreshCw, ShieldCheck, ShieldOff, Trash2, Wallet } from 'lucide-react';
import UserEditDrawer from './UserEditDrawer';
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
```

Key changes: remove `WorkerAssignmentModal` import; remove `KeyRound`, `Repeat` icons (no longer needed in table); add `UserEditDrawer` import.

- [ ] **Step 2.2: Replace `UserModalState` type (lines 34–38)**

The drawer handles edit/password/credit. Only `deleteUser` case remains unused (delete is handled by drawer too), so remove `UserModalState` entirely and the `ActionModal` component is no longer needed.

Replace lines 34–38:
```tsx
type UserModalState =
  | { type: 'editName'; user: AdminUserDto; fullName: string }
  | { type: 'resetPassword'; user: AdminUserDto; newPassword: string; confirmPassword: string }
  | { type: 'deleteUser'; user: AdminUserDto }
  | { type: 'grantCredit'; user: AdminUserDto; amount: string; reason: string };
```

With nothing (delete those 5 lines entirely — `UserModalState` is no longer used).

Also delete the `ActionModal` function (lines 57–140) and its `ActionModalProps` type (lines 57–68). The function can simply be removed since the drawer replaces all modal cases.

- [ ] **Step 2.3: Verify TypeScript still compiles (errors expected — continue to next tasks)**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -40
```

Expected: errors about `modal`, `setModal`, `openEditUserModal`, etc. — these will be fixed in Task 3.

---

## Task 3: Update `UserManagement.tsx` — state and handlers

**Files:**
- Modify: `frontend/src/islands/admin/UserManagement.tsx` (state block ~lines 142–165, handlers ~lines 281–465)

- [ ] **Step 3.1: Replace state block (lines 142–165)**

In `export default function UserManagement()`, replace the state declarations block. Remove `modal`, `modalError`, `assigningWorker`. Add `editingUser`.

```tsx
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
```

- [ ] **Step 3.2: Remove obsolete handler functions**

Delete these functions (they are now handled inside `UserEditDrawer`):
- `closeModal()` (~line 281)
- `openEditUserModal()` (~line 287)
- `openResetPasswordModal()` (~line 297)
- `openDeleteUserModal()` (~line 308)
- `openGrantCreditModal()` (~line 317)
- `submitEditUser()` (~line 368)
- `submitResetPassword()` (~line 394)
- `submitDeleteUser()` (~line 420)
- `submitGrantCredit()` (~line 442)

Keep:
- `handleToggleActive()` — still used by the block/unblock icon button in the table
- `handleToggleRole()` — no longer needed in table but keep as dead code for now (or delete)
- `loadCounters()`, `loadCurrentTab()`, `loadMetrics()`, `refreshData()` — keep all

- [ ] **Step 3.3: Verify**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -40
```

Expected: errors now only about column render functions referencing removed functions (`openEditUserModal` etc.). Fixed in Task 4.

---

## Task 4: Update `UserManagement.tsx` — simplify column action buttons

**Files:**
- Modify: `frontend/src/islands/admin/UserManagement.tsx` — `customerColumns` actions column (~lines 524–611) and `workerColumns` actions column (~lines 657–731)

- [ ] **Step 4.1: Replace customerColumns actions column**

Find the `key: 'actions'` entry inside `customerColumns` and replace its entire object with:

```tsx
{
  key: 'actions',
  header: 'Acciones',
  width: '100%',
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
        title="Gestionar crédito"
        onClick={(e) => { e.stopPropagation(); setEditingUser(row); }}
        disabled={busyUserId !== null}
        className="inline-flex items-center gap-1.5 px-3 py-2 text-[0.66rem] font-sans uppercase tracking-wider rounded-sm bg-pe-black text-pe-cream hover:bg-pe-charcoal disabled:opacity-45 transition-all"
      >
        <Wallet size={13} /> Crédito
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
```

- [ ] **Step 4.2: Replace workerColumns actions column**

Find the `key: 'actions'` entry inside `workerColumns` and replace with:

```tsx
{
  key: 'actions',
  header: 'Acciones',
  width: '100%',
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
```

- [ ] **Step 4.3: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -40
```

Expected: errors only about unused modal variables in the render return. Fixed in Task 5.

---

## Task 5: Update `UserManagement.tsx` — render return cleanup and wire drawer

**Files:**
- Modify: `frontend/src/islands/admin/UserManagement.tsx` — render return section (~lines 734–1022)

- [ ] **Step 5.1: Remove modal-related variables before return statement**

Delete these variable declarations (around lines 734–882):
- `const modalBusy = ...`
- `const modalTitle = ...`
- `const modalConfirmLabel = ...`
- `const modalConfirmTone = ...`
- `const modalBody = ...`
- `function handleModalConfirm() { ... }`

Keep `function handlePageChange()`.

- [ ] **Step 5.2: Replace ActionModal usage and add UserEditDrawer in return statement**

In the JSX return, find the `<ActionModal ...>` block (~lines 1007–1019) and replace it with the drawer render:

```tsx
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
```

The closing `</div>` and `}` of the component remain unchanged.

- [ ] **Step 5.3: Full TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1
```

Expected: 0 errors. If any remain, they will be about `handleToggleRole` being unused — either delete it or add `void handleToggleRole;` to suppress.

- [ ] **Step 5.4: Frontend build**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: build succeeds with no errors.

- [ ] **Step 5.5: Commit**

```bash
git add frontend/src/islands/admin/UserManagement.tsx
git commit -m "feat(admin): wire UserEditDrawer, simplify row actions, fix Rol button"
```

---

## Task 6: Verify in browser

**Files:** None (verification only)

- [ ] **Step 6.1: Start dev server**

```bash
cd frontend && npm run dev
```

- [ ] **Step 6.2: Open admin users page**

Navigate to `http://localhost:4321/admin/usuarios` (or `/es/admin/usuarios`).
Log in with admin credentials if redirected.

- [ ] **Step 6.3: Clientes tab — verify**

- [ ] Row shows: `[✏ Editar]` `[💳 Crédito]` `[🔒/🔓 icon]` `[🗑 icon]`
- [ ] No "Rol" button visible
- [ ] No "Reset pass" button visible
- [ ] No "Pasar a trabajador" button visible
- [ ] Clicking "Editar" or "Crédito" opens drawer from right side
- [ ] Drawer shows: header with avatar + name + status dot, Información básica, Estado toggle, Contraseña (collapsed), Crédito section with balance, footer zone
- [ ] Saving name updates the list (drawer stays open)
- [ ] Status toggle blocks/unblocks user
- [ ] Contraseña section expands with chevron, show/hide password eye icon works
- [ ] Crédito amount + reason + "Otorgar crédito" works
- [ ] "Pasar a trabajador" in footer works, closes drawer and refreshes list
- [ ] Delete: click "Eliminar usuario" → confirm prompt appears inline → "Sí, eliminar" → drawer closes

- [ ] **Step 6.4: Trabajadores tab — verify**

- [ ] Row shows: `[✏ Editar]` `[🔒/🔓 icon]` `[🗑 icon]`
- [ ] No "Rol" button visible in table
- [ ] No "Reset pass" button visible
- [ ] Clicking "Editar" opens drawer
- [ ] Drawer shows Rol laboral section (not Crédito)
- [ ] Rol laboral: selector for SUPERVISOR/ADMINISTRACION/DESPACHADOR/SELLER, dates, Asignar + Revocar buttons
- [ ] "Pasar a cliente" in footer works

- [ ] **Step 6.5: Dark mode — verify drawer appearance**

Toggle dark mode (if available in the admin). Drawer should show `bg-[#141414]`, light text, soft borders.

- [ ] **Step 6.6: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix(admin): visual polish user management drawer"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|---|---|
| Fix WorkerAssignmentModal (broken "Rol") | Task 1 (role logic inlined in drawer), Task 5 (removes broken assigningWorker) |
| Remove "Rol" from clientes tab | Task 4.1 (not included in customerColumns actions) |
| Consolidate editing into slide-out drawer | Task 1 |
| Reduce table to 3–4 buttons | Task 4.1 (4 buttons), Task 4.2 (3 buttons) |
| Luxury boutique aesthetic | Task 1 (full styling spec applied) |
| Soft borders, rounded-md | Task 1 (border-pe-black/8, rounded-md, rounded-sm) |
| Dark mode | Task 1 (dark: variants throughout) |
| Own-account guard | Task 1 (isSelf disables sections) |
| Section: info básica | Task 1 |
| Section: estado toggle | Task 1 |
| Section: contraseña collapsible | Task 1 |
| Section: crédito (clientes only) | Task 1 |
| Section: rol laboral (trabajadores only) | Task 1 |
| Footer: pasar / eliminar inline confirm | Task 1 |
| Success flash "Guardado ✓" 2s | Task 1 (`flashOk` helper) |
| Mobile w-full | Task 1 (`w-full max-w-[480px]`) |
| No backend changes | Confirmed — all API functions reused |
