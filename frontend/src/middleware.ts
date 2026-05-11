import { defineMiddleware } from 'astro:middleware';

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '=='.slice(0, (4 - (base64.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

const ADMIN_PANEL_ROLES = new Set(['ADMIN', 'SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER']);

export const onRequest = defineMiddleware(async (context, next) => {
  const { pathname } = context.url;

  if (pathname.startsWith('/admin') && !pathname.startsWith('/admin/login')) {
    const token = context.cookies.get('pe_token')?.value;

    if (!token) {
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    }

    const payload = decodeJwtPayload(token);
    if (!payload || !ADMIN_PANEL_ROLES.has(String(payload['role'] ?? ''))) {
      context.cookies.delete('pe_token', { path: '/' });
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    }

    if (typeof payload['exp'] === 'number' && Date.now() / 1000 > payload['exp']) {
      context.cookies.delete('pe_token', { path: '/' });
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    }

    // Server-side validation against backend to reject stale/invalid signatures.
    const apiBase = import.meta.env.INTERNAL_API_BASE_URL ?? 'http://backend:8080/api';
    try {
      const res = await fetch(`${apiBase}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        context.cookies.delete('pe_token', { path: '/' });
        return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
      }
      const me = await res.json();
      if (!me || !ADMIN_PANEL_ROLES.has(String(me.role ?? ''))) {
        context.cookies.delete('pe_token', { path: '/' });
        return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
      }
    } catch {
      context.cookies.delete('pe_token', { path: '/' });
      return context.redirect(`/admin/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }

  return next();
});
