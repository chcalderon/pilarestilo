import { useState, useEffect, useRef } from 'react';
import { User, LogOut, ChevronDown, LogIn } from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';
import { isAdminPanelRole, roleLabel } from '../../lib/roles';
import { getAuthMe, isAuthRejection } from '../../lib/api';
import { RegisterPopoverTrigger } from '@/components/auth/RegisterPopoverTrigger';

interface Props {
  locale: 'es' | 'en';
}

export default function AccountMenu({ locale }: Props) {
  const { user, token, setAuth, clearAuth } = useAuthStore();
  const [open, setOpen] = useState(false);
  const [ready, setReady] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const es = locale === 'es';

  // Validate stored token on mount
  useEffect(() => {
    async function validate() {
      if (token) {
        try {
          const me = await getAuthMe(token);
          setAuth(token, {
            id: me.id,
            email: me.email,
            role: me.role,
            fullName: me.fullName,
            avatarUrl: me.avatarUrl,
            permissions: user?.permissions ?? [],
            permissionCodes: user?.permissionCodes ?? [],
            vigencyStart: user?.vigencyStart,
            vigencyEnd: user?.vigencyEnd,
          });
        } catch (err) {
          // clearAuth() also drops the pe_token cookie the admin panel depends on, so only a
          // real rejection ends the session — a backend blip must not sign the user out.
          if (isAuthRejection(err)) {
            clearAuth();
          }
        }
      }
      setReady(true);
    }
    validate();
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  if (!ready) {
    return (
      <div className="w-16 h-4 bg-[var(--pe-dropdown-border)] animate-pulse rounded-sm" aria-hidden="true" />
    );
  }

  if (!user) {
    return (
      <div className="flex items-center gap-3">
        <RegisterPopoverTrigger
          locale={locale}
          initialTab="login"
          variant="text"
          label="Log in"
          icon={<LogIn size={13} />}
          className="font-sans text-[0.68rem] uppercase tracking-[0.22em] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] transition-colors duration-200 inline-flex items-center gap-1.5 whitespace-nowrap"
        />
        <RegisterPopoverTrigger locale={locale} initialTab="register" />
      </div>
    );
  }

  const displayName = user.fullName?.trim().split(' ')[0] || user.email.split('@')[0];
  const initials = (user.fullName?.trim() || user.email).substring(0, 2).toUpperCase();

  function handleLogout() {
    clearAuth();
    window.location.href = `/${locale}/`;
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(v => !v)}
        className="flex items-center gap-2 font-sans text-[0.68rem] uppercase tracking-[0.22em] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] transition-colors duration-200"
        aria-expanded={open}
        aria-haspopup="true"
      >
        {user.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt={displayName}
            className="w-6 h-6 rounded-full object-cover"
          />
        ) : (
          <span className="w-6 h-6 rounded-full bg-pe-rose-action flex items-center justify-center text-pe-offwhite text-[0.6rem] font-medium">
            {initials}
          </span>
        )}
        <span className="hidden sm:inline max-w-[120px] truncate">{displayName}</span>
        <ChevronDown size={11} className={`transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-2 w-48 bg-[var(--pe-dropdown-bg)] border border-[var(--pe-dropdown-border)] shadow-xl z-50">
          <div className="px-4 py-3 border-b border-[var(--pe-dropdown-border)]">
            <p className="font-sans text-[0.72rem] text-[var(--pe-nav-subtle)] truncate">{user.email}</p>
            {isAdminPanelRole(user.role) && (
              <p className="font-sans text-[0.65rem] tracking-wider uppercase text-pe-rose-ink mt-0.5">
                {roleLabel(user.role, es)}
              </p>
            )}
          </div>
          <a
            href={`/${locale}/account`}
            className="flex items-center gap-2.5 px-4 py-2.5 font-sans text-[0.78rem] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] hover:bg-[var(--pe-dropdown-hover)] transition-colors duration-200"
            onClick={() => setOpen(false)}
          >
            <User size={13} />
            {es ? 'Mi cuenta' : 'My account'}
          </a>
          {isAdminPanelRole(user.role) && (
            <a
              href="/admin"
              className="flex items-center gap-2.5 px-4 py-2.5 font-sans text-[0.78rem] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] hover:bg-[var(--pe-dropdown-hover)] transition-colors duration-200"
              onClick={() => setOpen(false)}
            >
              <span className="text-[0.65rem] tracking-wider uppercase text-pe-rose-ink">Panel admin</span>
            </a>
          )}
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2.5 px-4 py-2.5 font-sans text-[0.78rem] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] hover:bg-[var(--pe-dropdown-hover)] transition-colors duration-200 border-t border-[var(--pe-dropdown-border)]"
          >
            <LogOut size={13} />
            {es ? 'Cerrar sesion' : 'Sign out'}
          </button>
        </div>
      )}
    </div>
  );
}
