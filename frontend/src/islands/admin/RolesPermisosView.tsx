import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

const VIEW_KEYS = ['dashboard', 'productos', 'usuarios', 'caja', 'despachos', 'configuracion', 'roles_permisos'];
const VIEW_LABELS: Record<string, string> = {
  dashboard: 'Dashboard', productos: 'Productos', usuarios: 'Usuarios',
  caja: 'Caja', despachos: 'Despachos', configuracion: 'Configuración',
  roles_permisos: 'Roles/Permisos',
};
const EDITABLE_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'];

interface PermissionEntry { role: string; viewKey: string; }
type Matrix = Record<string, Set<string>>;

export default function RolesPermisosView() {
  const { token } = useAuthStore();
  const [matrix, setMatrix] = useState<Matrix>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    fetch('/api/admin/permissions', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json())
      .then((data: { permissions: PermissionEntry[] }) => {
        const m: Matrix = {};
        EDITABLE_ROLES.forEach(r => { m[r] = new Set(); });
        data.permissions.forEach(e => { if (m[e.role]) m[e.role].add(e.viewKey); });
        setMatrix(m);
      })
      .finally(() => setLoading(false));
  }, [token]);

  function toggle(role: string, viewKey: string) {
    setMatrix(prev => {
      const next = { ...prev, [role]: new Set(prev[role]) };
      if (next[role].has(viewKey)) next[role].delete(viewKey);
      else next[role].add(viewKey);
      return next;
    });
  }

  async function save() {
    setSaving(true); setError(''); setSaved(false);
    const permissions: PermissionEntry[] = [];
    EDITABLE_ROLES.forEach(role => {
      matrix[role]?.forEach(viewKey => permissions.push({ role, viewKey }));
    });
    try {
      const r = await fetch('/api/admin/permissions', {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ permissions }),
      });
      if (!r.ok) throw new Error('Error al guardar');
      setSaved(true);
    } catch { setError('Error al guardar los permisos.'); }
    finally { setSaving(false); }
  }

  if (loading) return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  return (
    <div className="space-y-6">
      <div className="overflow-x-auto">
        <table className="text-sm w-full border-collapse">
          <thead>
            <tr>
              <th className="text-left py-2 pr-4 font-sans text-[0.65rem] tracking-widest uppercase text-pe-charcoal/40">Vista</th>
              {EDITABLE_ROLES.map(role => (
                <th key={role} className="text-center py-2 px-3 font-sans text-[0.65rem] tracking-widest uppercase text-pe-charcoal/40">{role}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {VIEW_KEYS.map(viewKey => (
              <tr key={viewKey} className="border-t border-pe-sand/30">
                <td className="py-2 pr-4 text-pe-charcoal/70">{VIEW_LABELS[viewKey]}</td>
                {EDITABLE_ROLES.map(role => (
                  <td key={role} className="py-2 px-3 text-center">
                    <input
                      type="checkbox"
                      checked={matrix[role]?.has(viewKey) ?? false}
                      onChange={() => toggle(role, viewKey)}
                      className="accent-[#B76E79] w-4 h-4"
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}
      {saved && <p className="text-green-600 text-sm">Permisos guardados. Los cambios aplican en el próximo inicio de sesión.</p>}

      <button
        onClick={save}
        disabled={saving}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
      >
        {saving ? 'Guardando...' : 'Guardar cambios'}
      </button>
    </div>
  );
}
