import { defineMiddleware } from 'astro:middleware';
import { decodeJwtPayload } from './lib/jwt';
import { isAdminPanelRole } from './lib/roles';

const RBAC_DEBUG_ENABLED = import.meta.env.DEV || import.meta.env.RBAC_DEBUG === 'true';

type HybridRouteRequirement = {
  permissionCode: string;
  legacyViewKey: 'roles_permisos' | 'usuarios' | 'configuracion';
};

const HYBRID_ROUTE_REQUIREMENTS: Array<[string, HybridRouteRequirement]> = [
  ['/admin/roles-permisos', { permissionCode: 'roles.read', legacyViewKey: 'roles_permisos' }],
  ['/admin/users', { permissionCode: 'users.read', legacyViewKey: 'usuarios' }],
  ['/admin/settings', { permissionCode: 'settings.read', legacyViewKey: 'configuracion' }],
];

function resolveHybridRequirement(pathname: string): HybridRouteRequirement | null {
  for (const [prefix, requirement] of HYBRID_ROUTE_REQUIREMENTS) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      return requirement;
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
  return {
    allowed: legacyPermissions.includes(requirement.legacyViewKey),
    usedLegacyFallback: legacyPermissions.includes(requirement.legacyViewKey),
  };
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
    const apiBase = import.meta.env.INTERNAL_API_BASE_URL ?? 'http://backend:8080/api';
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
            hybridRequirement.legacyViewKey,
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

  return next();
});
