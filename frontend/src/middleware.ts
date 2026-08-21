import { defineMiddleware } from 'astro:middleware';
import { decodeJwtPayload } from './lib/jwt';
import { isAdminPanelRole } from './lib/roles';

const RBAC_DEBUG_ENABLED = import.meta.env.DEV || import.meta.env.RBAC_DEBUG === 'true';

type HybridRouteRequirement = {
  permissionCode: string;
  /**
   * The pre-RBAC view key that also opens the route. Optional: a screen added after the modern
   * permissions exist has no legacy equivalent, and inventing one would hand it to every account
   * that happens to hold an old key.
   */
  legacyViewKey?: 'roles_permisos' | 'usuarios' | 'configuracion' | 'caja';
};

const HYBRID_ROUTE_REQUIREMENTS: Array<[string, HybridRouteRequirement]> = [
  ['/admin/roles-permisos', { permissionCode: 'roles.read', legacyViewKey: 'roles_permisos' }],
  ['/admin/users', { permissionCode: 'users.read', legacyViewKey: 'usuarios' }],
  ['/admin/settings', { permissionCode: 'settings.read', legacyViewKey: 'configuracion' }],
  // The sales screen exposes buyer names, emails and amounts, so it is gated at the route as well
  // as in the island. The backend guards every endpoint behind it independently.
  ['/admin/ventas', { permissionCode: 'orders.read', legacyViewKey: 'caja' }],
  ['/admin/devoluciones', { permissionCode: 'returns.read', legacyViewKey: 'caja' }],
  // No legacy view key: privacy.read arrived with V84 and the old keys predate it, so an
  // account without the modern permission has no business reading who asked to be erased.
  ['/admin/privacidad', { permissionCode: 'privacy.read' }],
];

function resolveHybridRequirement(pathname: string): HybridRouteRequirement | null {
  for (const [prefix, requirement] of HYBRID_ROUTE_REQUIREMENTS) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      return requirement;
    }
  }
  return null;
}

const CHECKOUT_LOCALES = ['es', 'en'] as const;

/** Returns the locale when `pathname` is that locale's checkout, otherwise null. */
function resolveCheckoutLocale(pathname: string): (typeof CHECKOUT_LOCALES)[number] | null {
  for (const locale of CHECKOUT_LOCALES) {
    const prefix = `/${locale}/checkout`;
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      return locale;
    }
  }
  return null;
}

function readStringArray(source: unknown): string[] {
  return Array.isArray(source) ? source.filter((item): item is string => typeof item === 'string') : [];
}

function hasHybridRouteAccess(
  role: string,
  permissionCodes: string[],
  legacyPermissions: string[],
  requirement: HybridRouteRequirement,
): { allowed: boolean; usedLegacyFallback: boolean } {
  if (role === 'ADMIN') {
    return { allowed: true, usedLegacyFallback: false };
  }
  if (permissionCodes.includes(requirement.permissionCode)) {
    return { allowed: true, usedLegacyFallback: false };
  }
  const legacyAllows =
    requirement.legacyViewKey !== undefined && legacyPermissions.includes(requirement.legacyViewKey);
  return { allowed: legacyAllows, usedLegacyFallback: legacyAllows };
}

export const onRequest = defineMiddleware(async (context, next) => {
  const { pathname } = context.url;

  if (pathname.startsWith('/admin') && !pathname.startsWith('/admin/login')) {
    const token = context.cookies.get('pe_token')?.value;

    if (!token) {
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    }

    /** The token itself is bad — drop it so the stale copy stops being retried. */
    const rejectToken = () => {
      context.cookies.delete('pe_token', { path: '/' });
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    };

    const payload = decodeJwtPayload(token);
    if (!payload || !isAdminPanelRole(payload['role'])) {
      return rejectToken();
    }

    if (typeof payload['exp'] === 'number' && Date.now() / 1000 > payload['exp']) {
      return rejectToken();
    }

    /**
     * The backend could not answer. That says nothing about the token, so keep the cookie —
     * otherwise a transient outage logs every staff member out and they cannot get back in
     * until it recovers.
     */
    const backendUnavailable = () =>
      context.redirect(
        `/admin/login?redirect=${encodeURIComponent(pathname)}&reason=backend_unavailable`,
      );

    // Server-side validation against backend to reject stale/invalid signatures.
    // Same internal Docker address as in lib/api.ts: SSR to backend, never a browser.
    const apiBase = import.meta.env.INTERNAL_API_BASE_URL ?? 'http://backend:8080/api'; // NOSONAR
    let res: Response;
    try {
      res = await fetch(`${apiBase}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch {
      // Network-level failure: backend down, DNS, or a bad INTERNAL_API_BASE_URL.
      return backendUnavailable();
    }

    if (!res.ok) {
      return rejectToken();
    }

    let me: { role?: unknown; permissions?: unknown; permissionCodes?: unknown } | null;
    try {
      me = await res.json();
    } catch {
      // 200 with an unreadable body is a backend fault, not a bad credential.
      return backendUnavailable();
    }

    if (!me || !isAdminPanelRole(me.role)) {
      return rejectToken();
    }

    const hybridRequirement = resolveHybridRequirement(pathname);
    if (hybridRequirement) {
      const role = String(me.role ?? payload['role'] ?? '');
      const permissionCodes = readStringArray(me.permissionCodes ?? payload['permissionCodes']);
      const legacyPermissions = readStringArray(me.permissions ?? payload['permissions']);
      const access = hasHybridRouteAccess(role, permissionCodes, legacyPermissions, hybridRequirement);

      if (RBAC_DEBUG_ENABLED) {
        if (permissionCodes.length === 0 && legacyPermissions.length > 0) {
          console.info(
            '[RBAC] middleware legacy fallback route=%s role=%s permission=%s legacy=%s',
            pathname,
            role,
            hybridRequirement.permissionCode,
            hybridRequirement.legacyViewKey ?? 'none',
          );
        }
        if (access.allowed) {
          console.info(
            '[RBAC] middleware hybrid route=%s role=%s permission=%s authorities=%s fallback=%s',
            pathname,
            role,
            hybridRequirement.permissionCode,
            permissionCodes.length,
            access.usedLegacyFallback,
          );
        }
      }

      if (!access.allowed) {
        if (RBAC_DEBUG_ENABLED) {
          console.warn(
            '[RBAC] middleware deny route=%s role=%s permission=%s permissionCodes=%s legacyPermissions=%s',
            pathname,
            role,
            hybridRequirement.permissionCode,
            permissionCodes.length,
            legacyPermissions.length,
          );
        }
        return context.redirect('/admin/');
      }
    }
  }

  const checkoutLocale = resolveCheckoutLocale(pathname);
  if (checkoutLocale) {
    const token = context.cookies.get('pe_token')?.value;
    const payload = token ? decodeJwtPayload(token) : null;
    const expired =
      !!payload && typeof payload['exp'] === 'number' && Date.now() / 1000 > payload['exp'];

    /**
     * Deliberately a local check, unlike the admin gate above: it never calls the backend.
     * Checkout is not privileged — the order endpoint authorizes the request itself — so the
     * only job here is to avoid rendering a flow the customer cannot finish. Asking the
     * backend would mean a blip bounces a paying customer to the login screen.
     */
    if (!payload || expired) {
      if (token) {
        context.cookies.delete('pe_token', { path: '/' });
      }
      const target = `${pathname}${context.url.search}`;
      return context.redirect(
        `/${checkoutLocale}/auth/login?redirect=${encodeURIComponent(target)}`,
      );
    }
  }

  return next();
});
