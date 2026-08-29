import { useState } from 'react';
import { X } from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';

const WORKER_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'];

interface Props {
  readonly userId: string;
  readonly userFullName: string;
  readonly currentRole: string;
  readonly onClose: () => void;
  readonly onSaved: () => void;
}

export default function WorkerAssignmentModal({ userId, userFullName, currentRole, onClose, onSaved }: Props) {
  const { token } = useAuthStore();
  const [role, setRole] = useState(WORKER_ROLES.includes(currentRole) ? currentRole : WORKER_ROLES[0]);
  const [vigencyStart, setVigencyStart] = useState('');
  const [vigencyEnd, setVigencyEnd] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function assign() {
    setSaving(true); setError('');
    try {
      const r = await fetch(`/api/admin/workers/${userId}/assign`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ role, vigencyStart: vigencyStart || null, vigencyEnd: vigencyEnd || null }),
      });
      if (!r.ok) throw new Error('Error al asignar');
      onSaved(); onClose();
    } catch { setError('No se pudo asignar el rol.'); }
    finally { setSaving(false); }
  }

  async function revoke() {
    setSaving(true); setError('');
    try {
      const r = await fetch(`/api/admin/workers/${userId}/revoke`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!r.ok) throw new Error('Error al revocar');
      onSaved(); onClose();
    } catch { setError('No se pudo revocar el rol.'); }
    finally { setSaving(false); }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white w-full max-w-md mx-4 p-6 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-display text-xl text-pe-black">Asignar rol trabajador</h2>
          <button type="button" onClick={onClose} className="text-pe-muted hover:text-pe-black"><X size={20} /></button>
        </div>
        <p className="text-pe-muted text-sm mb-5">{userFullName}</p>
        <div className="space-y-4">
          <div>
            <label htmlFor="wam-role" className="block text-[10px] tracking-widest uppercase text-pe-muted mb-1">Rol</label>
            <select id="wam-role" value={role} onChange={e => setRole(e.target.value)}
              className="w-full border border-pe-black/10 bg-transparent px-3 py-2 text-sm focus:outline-hidden focus:border-pe-rose">
              {WORKER_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="wam-vigency-start" className="block text-[10px] tracking-widest uppercase text-pe-muted mb-1">Inicio vigencia</label>
            <input id="wam-vigency-start" type="date" value={vigencyStart} onChange={e => setVigencyStart(e.target.value)}
              className="w-full border border-pe-black/10 bg-transparent px-3 py-2 text-sm focus:outline-hidden focus:border-pe-rose" />
          </div>
          <div>
            <label htmlFor="wam-vigency-end" className="block text-[10px] tracking-widest uppercase text-pe-muted mb-1">Fin vigencia (opcional)</label>
            <input id="wam-vigency-end" type="date" value={vigencyEnd} onChange={e => setVigencyEnd(e.target.value)}
              className="w-full border border-pe-black/10 bg-transparent px-3 py-2 text-sm focus:outline-hidden focus:border-pe-rose" />
          </div>
        </div>
        {error && <p className="text-pe-danger-ink text-sm mt-3">{error}</p>}
        <div className="flex gap-3 mt-6">
          <button type="button" onClick={assign} disabled={saving}
            className="flex-1 bg-pe-black text-pe-offwhite py-2.5 text-xs tracking-widest uppercase hover:bg-pe-rose transition-colors disabled:opacity-50">
            {saving ? 'Guardando...' : 'Asignar rol'}
          </button>
          {WORKER_ROLES.includes(currentRole) && (
            <button type="button" onClick={revoke} disabled={saving}
              className="px-4 border border-pe-danger/40 text-pe-danger-ink text-xs tracking-widest uppercase hover:bg-pe-danger-surface transition-colors disabled:opacity-50">
              Revocar
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
