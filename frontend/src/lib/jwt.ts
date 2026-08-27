/**
 * JWT payload decoding shared by the SSR middleware and the client auth store.
 * Signature is NOT verified here — the backend is the authority. This only reads
 * claims (role, exp, permissions) to drive routing and cookie lifetime.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replaceAll('-', '+').replaceAll('_', '/');
    const padded = base64 + '=='.slice(0, (4 - (base64.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** Seconds until the token expires. Null when `exp` is missing or unreadable. */
export function secondsUntilExpiry(token: string): number | null {
  const payload = decodeJwtPayload(token);
  const exp = payload?.['exp'];
  if (typeof exp !== 'number') return null;
  return Math.floor(exp - Date.now() / 1000);
}

export function isTokenExpired(token: string): boolean {
  const remaining = secondsUntilExpiry(token);
  return remaining !== null && remaining <= 0;
}
