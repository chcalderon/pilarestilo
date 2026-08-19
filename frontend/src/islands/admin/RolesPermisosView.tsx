import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';
import { useCan } from '../../lib/permissions';

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
  const canRead = useCan('roles.read', 'roles_permisos');
  const canManage = useCan('roles.manage');
  const [matrix, setMatrix] = useState<Matrix>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!token || !canRead) {
      setLoading(false);
      return;
    }
    fetch('/api/admin/permissions', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => {
        if (!r.ok) {
          throw new Error('Error al cargar permisos');
        }
        return r.json();
      })
      .then((data: { permissions: PermissionEntry[] }) => {
        const m: Matrix = {};
        EDITABLE_ROLES.forEach(r => { m[r] = new Set(); });
        data.permissions.forEach(e => { if (m[e.role]) m[e.role].add(e.viewKey); });
        setMatrix(m);
      })
      .catch(() => setError('No se pudo cargar la matriz de permisos.'))
      .finally(() => setLoading(false));
  }, [token, canRead]);

  function toggle(role: string, viewKey: string) {
    if (!canManage) return;
    setMatrix(prev => {
      const next = { ...prev, [role]: new Set(prev[role]) };
      if (next[role].has(viewKey)) next[role].delete(viewKey);
      else next[role].add(viewKey);
      return next;
    });
  }

  async function save() {
    if (!canManage) return;
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

  if (!canRead) {
    return (
      <div className="border border-pe-black/10 bg-pe-white p-6">
        <p className="font-sans text-[0.66rem] uppercase tracking-[0.18em] text-pe-muted">Roles y permisos</p>
        <h2 className="mt-2 font-display text-2xl font-light text-pe-black">Acceso restringido</h2>
        <p className="mt-2 max-w-xl font-sans text-[0.8rem] leading-relaxed text-pe-muted">
          Tu sesion no tiene capacidad para consultar la matriz RBAC. Si este acceso deberia estar disponible,
          inicia sesion de nuevo o solicita habilitacion al equipo administrador.
        </p>
      </div>
    );
  }

  if (loading) return <p className="text-pe-muted text-sm">Cargando...</p>;

  return (
    <div className="space-y-6">
      {!canManage && (
        <div className="border border-pe-black/10 bg-pe-offwhite px-4 py-3">
          <p className="font-sans text-[0.72rem] text-pe-muted">
            Modo consulta. Puedes revisar la matriz vigente, pero los cambios solo estan habilitados para sesiones con <span className="font-mono text-[0.7rem]">roles.manage</span>.
          </p>
        </div>
      )}
      <div className="overflow-x-auto">
        <table className="text-sm w-full border-collapse">
          <thead>
            <tr>
              <th className="text-left py-2 pr-4 font-sans text-[0.65rem] tracking-widest uppercase text-pe-muted">Vista</th>
              {EDITABLE_ROLES.map(role => (
                <th key={role} className="text-center py-2 px-3 font-sans text-[0.65rem] tracking-widest uppercase text-pe-muted">{role}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {VIEW_KEYS.map(viewKey => (
              <tr key={viewKey} className="border-t border-pe-sand/30">
                <td className="py-2 pr-4 text-pe-muted">{VIEW_LABELS[viewKey]}</td>
                {EDITABLE_ROLES.map(role => (
                  <td key={role} className="py-2 px-3 text-center">
                    <input
                      type="checkbox"
                      checked={matrix[role]?.has(viewKey) ?? false}
                      onChange={() => toggle(role, viewKey)}
                      disabled={!canManage}
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
      {saved && <p className="text-pe-positive text-sm">Permisos guardados. Los cambios aplican en el próximo inicio de sesión.</p>}

      <button
        onClick={save}
        disabled={saving || !canManage}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
      >
        {!canManage ? 'Solo lectura' : saving ? 'Guardando...' : 'Guardar cambios'}
      </button>
    </div>
  );
}
