import { useState, useEffect } from 'react';
import { Plus, Trash2, Loader2, Check, X, Ticket, RefreshCw } from 'lucide-react';
import {
  listDiscountCodes, createDiscountCode, deleteDiscountCode, suggestDiscountCode,
  searchUsers, type UserSearchResultDto,
  type DiscountCodeDto, type CreateDiscountCodeRequest,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';

type Tab = 'active' | 'expired' | 'all';

const INPUT_CLASS =
  'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors w-full';

const EMPTY_FORM: CreateDiscountCodeRequest = {
  code: '', type: 'PERCENTAGE', value: 10, minOrderAmount: 0,
  validFrom: new Date().toISOString().slice(0, 10),
  validUntil: new Date(Date.now() + 30 * 86400_000).toISOString().slice(0, 10),
  maxUses: 1,
};

function isExpired(dto: DiscountCodeDto) {
  return !dto.active || new Date(dto.validUntil) < new Date();
}

function fmtDate(d: string) {
  return new Date(d).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function StatusBadge({ dto }: { dto: DiscountCodeDto }) {
  if (!dto.active) return (
    <span className="font-sans text-[0.6rem] uppercase tracking-wider bg-pe-charcoal/8 text-pe-muted px-1.5 py-0.5">Inactivo</span>
  );
  if (isExpired(dto)) return (
    <span className="font-sans text-[0.6rem] uppercase tracking-wider bg-red-50 text-red-400 px-1.5 py-0.5">Caducado</span>
  );
  return (
    <span className="font-sans text-[0.6rem] uppercase tracking-wider bg-green-50 text-green-800 px-1.5 py-0.5">Vigente</span>
  );
}

export default function DiscountCodeManager() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const [tab, setTab] = useState<Tab>('active');
  const [codes, setCodes] = useState<DiscountCodeDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CreateDiscountCodeRequest>({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [suggesting, setSuggesting] = useState(false);
  const [userQuery, setUserQuery] = useState('');
  const [userResults, setUserResults] = useState<UserSearchResultDto[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserSearchResultDto | null>(null);
  const [userSearchOpen, setUserSearchOpen] = useState(false);

  async function load(t: Tab = tab) {
    setLoading(true);
    try {
      const data = await listDiscountCodes(effectiveToken!, t);
      setCodes(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [tab]);

  useEffect(() => {
    if (!userQuery.trim() || !effectiveToken) {
      setUserResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const results = await searchUsers(userQuery, effectiveToken);
        setUserResults(results);
        setUserSearchOpen(results.length > 0);
      } catch {
        setUserResults([]);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [userQuery, effectiveToken]);

  async function handleSuggest() {
    if (!effectiveToken) return;
    setSuggesting(true);
    try {
      const suggested = await suggestDiscountCode(effectiveToken);
      setForm(f => ({ ...f, code: suggested }));
    } finally {
      setSuggesting(false);
    }
  }

  async function handleCreate() {
    if (!effectiveToken || !form.code || !form.value || !form.validFrom || !form.validUntil) {
      setError('Completa todos los campos requeridos.');
      return;
    }
    setSaving(true); setError('');
    try {
      await createDiscountCode({ ...form, assignedUserId: selectedUser?.id ?? null }, effectiveToken);
      setShowForm(false);
      setForm({ ...EMPTY_FORM });
      setSelectedUser(null);
      setUserQuery('');
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Error al crear código.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string, code: string) {
    if (!effectiveToken || !confirm(`¿Eliminar código "${code}"?`)) return;
    try {
      await deleteDiscountCode(id, effectiveToken);
      await load();
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : 'Error al eliminar.');
    }
  }

  const TABS: { key: Tab; label: string }[] = [
    { key: 'active', label: 'Vigentes' },
    { key: 'expired', label: 'Caducados' },
    { key: 'all', label: 'Todos' },
  ];

  return (
    <div>
      {/* Header */}
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex gap-1">
          {TABS.map(t => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={[
                'font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 transition-colors',
                tab === t.key
                  ? 'bg-[#3A3A3A] text-white'
                  : 'border border-pe-black/12 text-pe-muted hover:border-pe-charcoal/30',
              ].join(' ')}
            >
              {t.label}
            </button>
          ))}
        </div>
        <button
          onClick={() => { setShowForm(v => !v); setError(''); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-action-action-deep transition-colors"
        >
          {showForm ? <X size={13} /> : <Plus size={13} />}
          {showForm ? 'Cancelar' : 'Nuevo código'}
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="mb-4 bg-pe-cream/50 border border-pe-black/8 p-4">
          <p className="font-sans text-[0.65rem] uppercase tracking-wider text-pe-muted mb-3">
            Nuevo código de descuento
          </p>
          {error && <p className="font-sans text-[0.72rem] text-pe-rose-ink mb-2">{error}</p>}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {/* Code */}
            <div className="flex flex-col gap-0.5 sm:col-span-2 lg:col-span-1">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Código *</label>
              <div className="flex gap-1">
                <input
                  className={INPUT_CLASS}
                  value={form.code}
                  onChange={e => setForm(f => ({ ...f, code: e.target.value.toUpperCase() }))}
                  placeholder="PILAR_PE_ESTILO_000001"
                />
                <button
                  type="button"
                  onClick={handleSuggest}
                  disabled={suggesting}
                  className="shrink-0 px-2 border border-pe-black/12 text-pe-muted hover:text-pe-rose-ink hover:border-pe-rose/40 transition-colors"
                  title="Auto-sugerir código"
                >
                  {suggesting ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />}
                </button>
              </div>
            </div>

            {/* Type */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Tipo *</label>
              <select
                className={INPUT_CLASS}
                value={form.type}
                onChange={e => setForm(f => ({ ...f, type: e.target.value }))}
              >
                <option value="PERCENTAGE">Porcentaje (%)</option>
                <option value="FIXED">Monto fijo</option>
              </select>
            </div>

            {/* Value */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
                Valor * {form.type === 'PERCENTAGE' ? '(%)' : '($)'}
              </label>
              <input
                type="number"
                min="0.01"
                max={form.type === 'PERCENTAGE' ? 100 : undefined}
                step="0.01"
                className={INPUT_CLASS}
                value={form.value}
                onChange={e => setForm(f => ({ ...f, value: Number(e.target.value) }))}
              />
            </div>

            {/* Min order amount */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Monto mínimo</label>
              <input
                type="number"
                min="0"
                className={INPUT_CLASS}
                value={form.minOrderAmount ?? 0}
                onChange={e => setForm(f => ({ ...f, minOrderAmount: Number(e.target.value) }))}
              />
            </div>

            {/* Valid from */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Válido desde *</label>
              <input
                type="date"
                className={INPUT_CLASS}
                value={form.validFrom}
                onChange={e => setForm(f => ({ ...f, validFrom: e.target.value }))}
              />
            </div>

            {/* Valid until */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Válido hasta *</label>
              <input
                type="date"
                className={INPUT_CLASS}
                value={form.validUntil}
                onChange={e => setForm(f => ({ ...f, validUntil: e.target.value }))}
              />
            </div>

            {/* Max uses */}
            <div className="flex flex-col gap-0.5">
              <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Usos máximos</label>
              <input
                type="number"
                min="1"
                className={INPUT_CLASS}
                value={form.maxUses}
                onChange={e => setForm(f => ({ ...f, maxUses: Number(e.target.value) }))}
              />
            </div>

            {/* User assignment */}
            <div style={{ position: 'relative' }}>
              <label className="font-sans text-xs text-pe-muted uppercase tracking-wider block mb-1">
                Asignar a usuario (opcional)
              </label>
              {selectedUser ? (
                <div className="flex items-center gap-2 border border-pe-black/20 px-3 py-2 text-sm font-sans">
                  <span className="text-pe-charcoal">{selectedUser.fullName}</span>
                  <span className="text-pe-muted text-xs">{selectedUser.email}</span>
                  <button
                    type="button"
                    onClick={() => { setSelectedUser(null); setUserQuery(''); }}
                    className="ml-auto text-pe-muted hover:text-pe-charcoal"
                  >
                    ×
                  </button>
                </div>
              ) : (
                <input
                  type="text"
                  value={userQuery}
                  onChange={e => setUserQuery(e.target.value)}
                  onFocus={() => userResults.length > 0 && setUserSearchOpen(true)}
                  onBlur={() => setTimeout(() => setUserSearchOpen(false), 150)}
                  placeholder="Buscar por nombre o email..."
                  className="w-full border border-pe-black/20 px-3 py-2 text-sm font-sans focus:outline-hidden focus:border-pe-rose"
                />
              )}
              {userSearchOpen && userResults.length > 0 && (
                <ul className="absolute z-50 w-full border border-pe-black/20 bg-white shadow-lg mt-0.5 max-h-48 overflow-y-auto">
                  {userResults.map(u => (
                    <li key={u.id}>
                      <button
                        type="button"
                        onMouseDown={() => { setSelectedUser(u); setUserQuery(''); setUserSearchOpen(false); }}
                        className="w-full text-left px-3 py-2 hover:bg-pe-rose/5 flex gap-2 items-center"
                      >
                        <span className="font-sans text-sm text-pe-charcoal">{u.fullName}</span>
                        <span className="font-sans text-xs text-pe-muted">{u.email}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="mt-3 flex gap-2">
            <button
              onClick={handleCreate}
              disabled={saving}
              className="flex items-center gap-1.5 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-4 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50"
            >
              {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
              Crear código
            </button>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-pe-white border border-pe-black/6 shadow-xs overflow-x-auto">
        {loading ? (
          <div className="flex justify-center py-16">
            <Loader2 size={22} className="animate-spin text-pe-rose-ink" />
          </div>
        ) : codes.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-16 text-pe-muted">
            <Ticket size={28} strokeWidth={1.2} />
            <p className="font-sans text-[0.82rem]">No hay códigos en esta categoría</p>
          </div>
        ) : (
          <table className="w-full min-w-[640px]">
            <thead>
              <tr className="border-b border-pe-black/6">
                {['Código', 'Descuento', 'Vigencia', 'Usos', 'Estado', ''].map(h => (
                  <th key={h} className="text-left font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted px-4 py-3">
                    {h}
                  </th>
                ))}
                <th className="text-left font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted px-4 py-3">
                  Disponible para
                </th>
              </tr>
            </thead>
            <tbody>
              {codes.map(c => (
                <tr key={c.id} className="border-b border-pe-black/4 hover:bg-pe-cream/30 transition-colors">
                  <td className="px-4 py-3">
                    <span className="font-mono text-[0.78rem] text-pe-charcoal tracking-wider">{c.code}</span>
                  </td>
                  <td className="px-4 py-3 font-sans text-[0.78rem] text-pe-charcoal">
                    {c.type === 'PERCENTAGE' ? `${c.value}%` : `$${c.value.toLocaleString('es-CL')}`}
                  </td>
                  <td className="px-4 py-3 font-sans text-[0.72rem] text-pe-muted whitespace-nowrap">
                    {fmtDate(c.validFrom)} – {fmtDate(c.validUntil)}
                  </td>
                  <td className="px-4 py-3 font-sans text-[0.78rem] text-pe-muted">
                    {c.timesUsed} / {c.maxUses}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge dto={c} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => handleDelete(c.id, c.code)}
                      className="p-1.5 text-pe-muted hover:text-red-500 transition-colors"
                      title="Eliminar"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                  <td className="font-sans text-xs text-pe-charcoal px-4 py-3">
                    {c.assignedUserName ? (
                      <span className="inline-flex items-center gap-1 border border-pe-black/15 px-2 py-0.5 text-xs">
                        {c.assignedUserName}
                      </span>
                    ) : (
                      <span className="text-pe-muted">Todos</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
