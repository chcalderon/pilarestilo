import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

/**
 * Characterization tests written before splitting the admin/checkout/auth-page gates out of the
 * single onRequest handler (S3776, complexity 57) -- it had none, despite being the entire
 * /admin auth gate, the checkout auth gate, and the storefront login/register redirect-away-if-
 * authenticated logic. Given the security stakes, every branch of every gate is covered here
 * before any refactor: missing/invalid/expired tokens, backend-down vs backend-rejects, the
 * hybrid RBAC permission-vs-legacy-view-key fallback, and each gate's independence from the
 * others (a non-matching path must fall through untouched).
 */

let onRequest: any;

function b64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function makeToken(payload: Record<string, unknown>): string {
  return `${b64url({ alg: 'none' })}.${b64url(payload)}.sig`;
}

function makeContext(pathname: string, opts: { token?: string; search?: string } = {}) {
  const cookieStore = new Map<string, string>();
  if (opts.token) cookieStore.set('pe_token', opts.token);
  const url = new URL(`https://shop.test${pathname}${opts.search ?? ''}`);
  return {
    url,
    cookies: {
      get: (name: string) => (cookieStore.has(name) ? { value: cookieStore.get(name)! } : undefined),
      delete: vi.fn((name: string) => { cookieStore.delete(name); }),
      __store: cookieStore,
    },
    redirect: vi.fn((path: string) => ({ __redirect: path })),
  };
}

const NEXT_MARKER = { __next: true };

beforeEach(async () => {
  vi.resetModules();
  ({ onRequest } = await import('../middleware'));
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('middleware: non-matching paths', () => {
  it('falls through untouched for a plain storefront path', async () => {
    const context = makeContext('/es/products');
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    const result = await onRequest(context, next);
    expect(result).toBe(NEXT_MARKER);
    expect(context.redirect).not.toHaveBeenCalled();
  });

  it('lets /admin/login through without a token', async () => {
    const context = makeContext('/admin/login');
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    const result = await onRequest(context, next);
    expect(result).toBe(NEXT_MARKER);
  });
});

describe('middleware: admin gate', () => {
  it('redirects to login with the original path when there is no token', async () => {
    const context = makeContext('/admin/dashboard');
    const next = vi.fn();
    const result: any = await onRequest(context, next);
    expect(result.__redirect).toBe('/admin/login?redirect=%2Fadmin%2Fdashboard');
    expect(next).not.toHaveBeenCalled();
  });

  it('rejects and clears the cookie for an undecodable token', async () => {
    const context = makeContext('/admin/dashboard', { token: 'not-a-jwt' });
    const next = vi.fn();
    const result: any = await onRequest(context, next);
    expect(result.__redirect).toMatch(/^\/admin\/login\?redirect=/);
    expect(context.cookies.delete).toHaveBeenCalledWith('pe_token', { path: '/' });
  });

  it('rejects a token whose role cannot open the admin panel', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/^\/admin\/login/);
    expect(context.cookies.delete).toHaveBeenCalled();
  });

  it('rejects an expired admin token without ever calling the backend', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) - 10 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/^\/admin\/login/);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('keeps the cookie and redirects with a backend_unavailable reason when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/reason=backend_unavailable/);
    expect(context.cookies.delete).not.toHaveBeenCalled();
  });

  it('keeps the cookie when the backend answers with unreadable JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.reject(new Error('bad json')),
    }));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/reason=backend_unavailable/);
    expect(context.cookies.delete).not.toHaveBeenCalled();
  });

  it('rejects and clears the cookie when the backend says the token is invalid', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/^\/admin\/login/);
    expect(context.cookies.delete).toHaveBeenCalled();
  });

  it('rejects when the backend reports a non-admin-panel role even if the token claimed one', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ role: 'CUSTOMER' }) }));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/^\/admin\/login/);
  });

  it('lets a verified admin through to a plain (non-hybrid) admin route', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ role: 'ADMIN' }) }));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/dashboard', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    const result = await onRequest(context, next);
    expect(result).toBe(NEXT_MARKER);
  });
});

describe('middleware: hybrid RBAC routes', () => {
  function adminMeFetch(me: Record<string, unknown>) {
    return vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(me) });
  }

  it('lets ADMIN through a hybrid route with no permission check at all', async () => {
    vi.stubGlobal('fetch', adminMeFetch({ role: 'ADMIN' }));
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/users', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
  });

  it('lets a SELLER through with the modern permission code', async () => {
    vi.stubGlobal('fetch', adminMeFetch({ role: 'SELLER', permissionCodes: ['users.read'] }));
    const token = makeToken({ role: 'SELLER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/users', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
  });

  it('falls back to the legacy view key when there are no modern permission codes', async () => {
    vi.stubGlobal('fetch', adminMeFetch({ role: 'SELLER', permissionCodes: [], permissions: ['usuarios'] }));
    const token = makeToken({ role: 'SELLER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/users', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
  });

  it('denies to /admin/ when neither the permission nor the legacy key is present', async () => {
    vi.stubGlobal('fetch', adminMeFetch({ role: 'SELLER', permissionCodes: [], permissions: [] }));
    const token = makeToken({ role: 'SELLER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/users', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/admin/');
  });

  it('denies a route with no legacy fallback (privacidad) when the permission is missing, even with unrelated legacy keys', async () => {
    vi.stubGlobal('fetch', adminMeFetch({ role: 'SELLER', permissionCodes: [], permissions: ['usuarios', 'caja'] }));
    const token = makeToken({ role: 'SELLER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/admin/privacidad', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/admin/');
  });
});

describe('middleware: checkout gate', () => {
  it('redirects to login, preserving the target and query string, and clears any stale token', async () => {
    const context = makeContext('/es/checkout', { token: 'garbage', search: '?paso=pago' });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/es/auth/login?redirect=%2Fes%2Fcheckout%3Fpaso%3Dpago');
    expect(context.cookies.delete).toHaveBeenCalledWith('pe_token', { path: '/' });
  });

  it('redirects an expired session out of checkout', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) - 10 });
    const context = makeContext('/en/checkout/summary', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toMatch(/^\/en\/auth\/login\?redirect=/);
  });

  it('never calls the backend for the checkout gate -- it is a local-only check', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/es/checkout', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('middleware: storefront auth-page gate', () => {
  it('lets an unauthenticated visitor see the login page', async () => {
    const context = makeContext('/es/auth/login');
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
  });

  it('redirects an already-authenticated customer to their account page', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/es/auth/register', { token });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/es/account');
  });

  it('honors a safe ?redirect= for an already-authenticated customer', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/es/auth/login', { token, search: '?redirect=%2Fes%2Fwishlist' });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/es/wishlist');
  });

  it('ignores an unsafe ?redirect= (protocol-relative) and falls back to the account page', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/es/auth/login', { token, search: '?redirect=%2F%2Fevil.test' });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/es/account');
  });

  it('sends an already-authenticated admin-panel role to the admin dashboard, ignoring redirect', async () => {
    const token = makeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });
    const context = makeContext('/en/auth/login', { token, search: '?redirect=%2Fen%2Faccount' });
    const result: any = await onRequest(context, vi.fn());
    expect(result.__redirect).toBe('/admin/dashboard');
  });

  it('lets an expired-token visitor see the auth page (treated as logged out)', async () => {
    const token = makeToken({ role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) - 10 });
    const context = makeContext('/es/auth/login', { token });
    const next = vi.fn().mockResolvedValue(NEXT_MARKER);
    expect(await onRequest(context, next)).toBe(NEXT_MARKER);
  });
});
