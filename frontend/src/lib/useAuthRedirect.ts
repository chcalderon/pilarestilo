import { useState } from "react";
import type { AuthTokenResponse } from "./api";
import { useAuthStore } from "./authStore";

export interface AuthSuccessState {
  readonly name: string;
  readonly merged: boolean;
}

/** Long enough to read a short line, short enough that waiting for it isn't sluggish. */
const WELCOME_DWELL_MS = 1600;
/** The merge message runs two lines and carries more to read. */
const MERGED_DWELL_MS = 2500;

/**
 * Shared by every password login/register form and the Google callback: all of them write to the
 * auth store, show a name, and redirect -- diverging only in where they redirect to, which is why
 * that part is a callback rather than a fixed path. A merged account gets a longer dwell because
 * its message runs two lines instead of one.
 */
export function useAuthRedirect(dest: (data: AuthTokenResponse) => string) {
  const [success, setSuccess] = useState<AuthSuccessState | null>(null);
  const { setAuth } = useAuthStore();

  function finishAuth(data: AuthTokenResponse) {
    setAuth(data.accessToken, {
      id: data.userId,
      email: data.email,
      role: data.role,
      fullName: data.fullName,
      avatarUrl: data.avatarUrl,
      permissions: data.permissions ?? [],
      permissionCodes: data.permissionCodes ?? [],
      vigencyStart: data.vigencyStart,
      vigencyEnd: data.vigencyEnd,
    });
    const name = data.fullName?.trim().split(" ")[0] || data.email.split("@")[0];
    const merged = !!data.accountMerged;
    setSuccess({ name, merged });
    window.setTimeout(() => {
      window.location.href = dest(data);
    }, merged ? MERGED_DWELL_MS : WELCOME_DWELL_MS);
  }

  return { success, finishAuth };
}
