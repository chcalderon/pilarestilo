import { create } from 'zustand';
import { persist } from 'zustand/middleware';

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

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
    }),
    { name: 'pe-auth' }
  )
);

export function readAuthTokenCookie(): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|;\s*)pe_token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
