import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { isTokenExpired, secondsUntilExpiry } from './jwt';

export interface StoredUser {
  id: string;
  email: string;
  role: string;
  fullName?: string;
  avatarUrl?: string;
  permissions: string[];
  permissionCodes: string[];
  vigencyStart?: string;
  vigencyEnd?: string;
}

interface AuthState {
  token: string | null;
  user: StoredUser | null;
  setAuth: (token: string, user: StoredUser) => void;
  clearAuth: () => void;
}

const COOKIE_NAME = 'pe_token';
/** Fallback lifetime when the token carries no readable `exp` (backend issues 24h access tokens). */
const FALLBACK_MAX_AGE_SECONDS = 86400;

/**
 * The `pe_token` cookie is the ONLY token the Astro SSR middleware can read, while the
 * Zustand/localStorage copy is what React islands read. Writing them from separate call
 * sites let them drift — a storefront login that skipped the cookie left admins bounced
 * back to /admin/login. Both are written here and nowhere else: never assign
 * `document.cookie = 'pe_token=…'` outside this module.
 */
function writeAuthTokenCookie(token: string): void {
  if (typeof document === 'undefined') return;
  const remaining = secondsUntilExpiry(token);
  const maxAge = remaining === null ? FALLBACK_MAX_AGE_SECONDS : Math.max(0, remaining);
  // Local dev is plain http://localhost; a hardcoded Secure would silently drop the cookie there.
  const secure = typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${COOKIE_NAME}=${encodeURIComponent(token)}; path=/; max-age=${maxAge}; SameSite=Lax${secure}`;
}

function clearAuthTokenCookie(): void {
  if (typeof document === 'undefined') return;
  document.cookie = `${COOKIE_NAME}=; path=/; max-age=0; SameSite=Lax`;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => {
        writeAuthTokenCookie(token);
        set({ token, user });
      },
      clearAuth: () => {
        clearAuthTokenCookie();
        set({ token: null, user: null });
      },
    }),
    {
      name: 'pe-auth',
      // Rehydration bypasses setAuth, so repair the cookie here: it may have expired on its
      // own (localStorage never does) or been deleted by the middleware on a transient failure.
      onRehydrateStorage: () => (state) => {
        if (!state?.token) return;
        if (isTokenExpired(state.token)) {
          state.clearAuth();
        } else {
          writeAuthTokenCookie(state.token);
        }
      },
    }
  )
);

export function readAuthTokenCookie(): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|;\s*)pe_token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
