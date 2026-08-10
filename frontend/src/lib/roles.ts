/**
 * Roles allowed into the /admin panel. Must stay in sync with the backend
 * UserRole enum minus CUSTOMER — the middleware and every login form gate on it.
 */
export const ADMIN_PANEL_ROLES = ['ADMIN', 'SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'] as const;

const ADMIN_PANEL_ROLE_SET: ReadonlySet<string> = new Set(ADMIN_PANEL_ROLES);

export function isAdminPanelRole(role: unknown): boolean {
  return typeof role === 'string' && ADMIN_PANEL_ROLE_SET.has(role);
}

/** Display label per role, used by the storefront account menu. */
export function roleLabel(role: string, es: boolean): string {
  switch (role) {
    case 'ADMIN': return 'Admin';
    case 'SUPERVISOR': return es ? 'Supervisor/a' : 'Supervisor';
    case 'ADMINISTRACION': return es ? 'Administración' : 'Administration';
    case 'DESPACHADOR': return es ? 'Despachador/a' : 'Dispatcher';
    case 'SELLER': return es ? 'Vendedor/a' : 'Seller';
    default: return es ? 'Cliente' : 'Customer';
  }
}
