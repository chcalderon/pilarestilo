import { LogOut, Store } from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';

/**
 * The two admin-shell actions that belong in the top bar, not buried in the sidebar footer:
 * jump to the public storefront (new tab, session untouched) and sign out. Sign-out reuses the
 * store's `clearAuth` so the cookie, the persisted state and the PostHog identity all reset the
 * same way the sidebar's button does.
 */
export default function AdminTopbarActions() {
  const { clearAuth } = useAuthStore();

  function handleLogout() {
    clearAuth();
    window.location.href = '/admin/login';
  }

  const control =
    'inline-flex items-center gap-1.5 h-[34px] px-2.5 border cursor-pointer ' +
    'font-sans text-[0.7rem] tracking-[0.04em] transition-colors duration-150 ' +
    'border-[color:var(--admin-border)] text-[color:var(--admin-muted)] ' +
    'hover:text-pe-rose-action hover:border-pe-rose-action/60 hover:bg-pe-rose-action/10';

  return (
    <>
      <a
        href="/"
        target="_blank"
        rel="noopener noreferrer"
        className={control}
        title="Ver tienda"
      >
        <Store size={14} className="flex-shrink-0" />
        <span className="hidden lg:inline">Ver tienda</span>
      </a>
      <button type="button" onClick={handleLogout} className={control} title="Cerrar sesión">
        <LogOut size={14} className="flex-shrink-0" />
        <span className="hidden lg:inline">Cerrar sesión</span>
      </button>
    </>
  );
}
