import { useEffect, useState } from 'react';
import {
  LayoutDashboard,
  Package,
  Tag,
  Star,
  CreditCard,
  Users,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Store,
  Wallet,
  Image,
  Bell,
  Ticket,
  ShieldCheck,
  ShieldOff,
  DollarSign,
  Truck,
  Sparkles,
  Megaphone,
  Navigation,
  Receipt,
  Undo2,
  SlidersHorizontal,
} from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { getPendingDocumentCount } from '../../lib/api';
import { useCan } from '../../lib/permissions';

interface Props {
  readonly currentPath: string;
  readonly mobile?: boolean;
}

type SettingsSubmenuTab = 'store' | 'payments' | 'media' | 'notifications' | 'shipping' | 'tributarios';

const navItems: Array<{ href: string; icon: typeof LayoutDashboard; label: string; viewKey: string }> = [
  { href: '/admin/', icon: LayoutDashboard, label: 'Dashboard', viewKey: 'dashboard' },
  { href: '/admin/products', icon: Package, label: 'Productos', viewKey: 'productos' },
  { href: '/admin/categories', icon: Tag, label: 'Categorias', viewKey: 'productos' },
  { href: '/admin/tipos-variante', icon: SlidersHorizontal, label: 'Tipos de Variante', viewKey: 'productos' },
  { href: '/admin/navegacion', icon: Navigation, label: 'Navegación', viewKey: 'productos' },
  { href: '/admin/reviews', icon: Star, label: 'Resenas', viewKey: 'productos' },
  { href: '/admin/ventas', icon: Receipt, label: 'Ventas', viewKey: 'caja' },
  { href: '/admin/payments', icon: CreditCard, label: 'Pagos', viewKey: 'caja' },
  { href: '/admin/caja', icon: DollarSign, label: 'Caja', viewKey: 'caja' },
  { href: '/admin/despachos', icon: Truck, label: 'Despachos', viewKey: 'despachos' },
  { href: '/admin/devoluciones', icon: Undo2, label: 'Devoluciones', viewKey: 'caja' },
  { href: '/admin/fichas-ia', icon: Sparkles, label: 'Fichas con IA', viewKey: 'productos' },
  { href: '/admin/publicaciones', icon: Megaphone, label: 'Publicaciones', viewKey: 'productos' },
  { href: '/admin/discounts', icon: Ticket, label: 'Descuentos', viewKey: 'productos' },
  { href: '/admin/users', icon: Users, label: 'Usuarios', viewKey: 'usuarios' },
  { href: '/admin/privacidad', icon: ShieldOff, label: 'Privacidad', viewKey: 'privacy.read' },
  { href: '/admin/roles-permisos', icon: ShieldCheck, label: 'Roles/Permisos', viewKey: 'roles_permisos' },
];

const settingsSubmenuItems: Array<{
  href: string;
  tab: SettingsSubmenuTab;
  icon: typeof Store;
  label: string;
}> = [
  { href: '/admin/settings?tab=store', tab: 'store', icon: Store, label: 'Canales tienda' },
  { href: '/admin/settings?tab=payments', tab: 'payments', icon: Wallet, label: 'Pagos' },
  { href: '/admin/settings?tab=shipping', tab: 'shipping', icon: Truck, label: 'Envios' },
  { href: '/admin/settings?tab=tributarios', tab: 'tributarios', icon: Receipt, label: 'Tributarios' },
  { href: '/admin/settings?tab=media', tab: 'media', icon: Image, label: 'Media' },
  { href: '/admin/settings?tab=notifications', tab: 'notifications', icon: Bell, label: 'Notificaciones' },
];

export default function AdminSidebar({ currentPath, mobile = false }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [settingsExpanded, setSettingsExpanded] = useState(currentPath.startsWith('/admin/settings'));
  const [activeSettingsTab, setActiveSettingsTab] = useState<SettingsSubmenuTab>('store');
  const [pendingDocuments, setPendingDocuments] = useState(0);
  const { user, token, clearAuth } = useAuthStore();
  const canSeeUsers = useCan('users.read', 'usuarios');
  const canSeeRoles = useCan('roles.read', 'roles_permisos');
  const canSeeSettings = useCan('settings.read', 'configuracion');
  // ADMINISTRACION and SUPERVISOR hold orders.read from V78 but were never given the legacy
  // 'caja' view key, so the modern code has to be enough on its own here.
  const canSeeSales = useCan('orders.read');
  const canSeeReturns = useCan('returns.read');
  const canSeePrivacy = useCan('privacy.read');

  const permissions = user?.permissions ?? [];
  const visibleNavItems = user?.role === 'ADMIN'
    ? navItems
    : navItems.filter((item) => {
      if (item.href === '/admin/users') return canSeeUsers;
      if (item.href === '/admin/roles-permisos') return canSeeRoles;
      if (item.href === '/admin/ventas') return canSeeSales || permissions.includes('caja');
      if (item.href === '/admin/devoluciones') return canSeeReturns || permissions.includes('caja');
      if (item.href === '/admin/privacidad') return canSeePrivacy;
      // VariantTemplateController requires ADMIN on every method (unlike categories), so a
      // non-ADMIN user must never see a link that always 403s.
      if (item.href === '/admin/tipos-variante') return false;
      return permissions.includes(item.viewKey);
    });
  const showSettings = canSeeSettings;

  const isCollapsed = mobile ? false : collapsed;
  const settingsRouteActive = currentPath.startsWith('/admin/settings');
  const showSettingsChildren = !isCollapsed && settingsExpanded;
  const settingsSubmenuId = mobile ? 'admin-settings-submenu-mobile' : 'admin-settings-submenu-desktop';

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const tab = params.get('tab');
    // Derived from the list rather than a hand-kept whitelist. The literal one omitted 'shipping',
    // so the Envios link never highlighted and always fell through to 'store'.
    const known = settingsSubmenuItems.find((item) => item.tab === tab);
    setActiveSettingsTab(known ? known.tab : 'store');
  }, []);

  /*
   * The count of paid sales still missing a boleta, shown as a badge so an undeclared sale is
   * visible without opening the screen. Only fetched on the copy of the sidebar that is actually
   * rendered for this viewport, and it fails to zero rather than blocking the menu.
   */
  useEffect(() => {
    if (mobile || !canSeeSales) return;
    const authToken = token ?? readAuthTokenCookie();
    if (!authToken) return;
    let cancelled = false;
    getPendingDocumentCount(authToken).then((count) => {
      if (!cancelled) setPendingDocuments(count);
    });
    return () => {
      cancelled = true;
    };
  }, [mobile, canSeeSales, token]);

  function closeMobileMenu() {
    if (!mobile) return;
    document.body.classList.remove('admin-mobile-open');
  }

  function handleLogout() {
    clearAuth();
    closeMobileMenu();
    window.location.href = '/admin/login';
  }

  const isActive = (href: string) =>
    href === '/admin/' ? currentPath === '/admin' || currentPath === '/admin/' : currentPath.startsWith(href);

  function handleSettingsToggle() {
    if (isCollapsed && !mobile) {
      window.location.href = '/admin/settings?tab=store';
      return;
    }
    setSettingsExpanded((value) => !value);
  }

  let sidebarWidth = '240px';
  if (mobile) {
    sidebarWidth = '100%';
  } else if (isCollapsed) {
    sidebarWidth = '60px';
  }

  return (
    <div
      className="flex flex-col h-full bg-pe-black text-pe-offwhite transition-all duration-300"
      style={{ width: sidebarWidth }}
    >
      <div className="flex items-center justify-between px-4 py-4 border-b border-pe-white/8 min-h-[64px]">
        <div className="flex items-center gap-2 overflow-hidden">
          <img src="/logo-pe.svg" alt="" width="28" height="28" aria-hidden="true" />
          {!isCollapsed && (
            <div className="flex flex-col leading-none">
              <span className="font-sans text-[0.58rem] tracking-[0.25em] uppercase text-pe-on-dark-muted">Pilar Estilo</span>
              <span className="font-sans text-[0.58rem] tracking-[0.2em] uppercase text-pe-on-dark-muted">Admin</span>
            </div>
          )}
        </div>

        {!mobile && (
          <button
            type="button"
            onClick={() => setCollapsed((v) => !v)}
            className="ml-auto p-1 text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors rounded-sm"
            aria-label={isCollapsed ? 'Expandir menu' : 'Colapsar menu'}
          >
            {isCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        )}
      </div>

      <nav className="flex-1 py-4 overflow-y-auto" aria-label="Navegacion admin">
        <ul className="flex flex-col gap-0.5 px-2">
          {visibleNavItems.map((item) => {
            const active = isActive(item.href);
            const Icon = item.icon;

            return (
              <li key={item.href}>
                <a
                  href={item.href}
                  onClick={closeMobileMenu}
                  className={[
                    'flex items-center gap-3 px-3 py-2.5 rounded-sm transition-colors duration-150 group',
                    'font-sans text-[0.78rem] tracking-[0.04em]',
                    active
                      ? 'bg-pe-rose/12 text-pe-rose-soft'
                      : 'text-pe-on-dark-muted hover:text-pe-on-dark hover:bg-pe-white/4',
                  ].join(' ')}
                  aria-current={active ? 'page' : undefined}
                  title={isCollapsed ? item.label : undefined}
                >
                  <Icon size={16} className="flex-shrink-0" />
                  {!isCollapsed && <span>{item.label}</span>}
                  {item.href === '/admin/ventas' && pendingDocuments > 0 && (
                    <span
                      className="ml-auto inline-flex items-center justify-center min-w-[1.25rem] px-1 py-0.5 text-[0.62rem] tabular-nums bg-pe-rose-action text-pe-white rounded-xs"
                      title={`${pendingDocuments} ventas pagadas sin boleta`}
                    >
                      {pendingDocuments}
                      <span className="sr-only"> ventas pagadas sin boleta</span>
                    </span>
                  )}
                </a>
              </li>
            );
          })}

          {showSettings && <li>
            <button
              type="button"
              onClick={handleSettingsToggle}
              className={[
                'w-full flex items-center gap-3 px-3 py-2.5 rounded-sm transition-all duration-200 group',
                'font-sans text-[0.78rem] tracking-[0.04em]',
                settingsRouteActive
                  ? 'bg-pe-rose/12 text-pe-rose-soft'
                  : 'text-pe-on-dark-muted hover:text-pe-on-dark hover:bg-pe-white/4',
              ].join(' ')}
              aria-expanded={showSettingsChildren}
              aria-controls={settingsSubmenuId}
              title={isCollapsed ? 'Configuracion' : undefined}
            >
              <Settings
                size={16}
                className={[
                  'flex-shrink-0 transition-transform duration-300',
                  showSettingsChildren ? 'rotate-90 text-pe-rose-soft' : '',
                ].join(' ')}
              />
              {!isCollapsed && <span className="text-left">Configuracion</span>}
              {!isCollapsed && (
                <ChevronRight
                  size={14}
                  className={[
                    'ml-auto transition-all duration-300',
                    showSettingsChildren ? 'rotate-90 text-pe-rose-soft' : 'text-pe-on-dark-muted',
                  ].join(' ')}
                />
              )}
            </button>

            <div
              id={settingsSubmenuId}
              className={[
                'grid overflow-hidden transition-all duration-300 ease-out',
                showSettingsChildren ? 'grid-rows-[1fr] opacity-100 mt-1' : 'grid-rows-[0fr] opacity-0',
              ].join(' ')}
            >
              <ul className="min-h-0 flex flex-col gap-1 pl-4 pr-1 pb-1">
                {settingsSubmenuItems.map((subitem) => {
                  const SubIcon = subitem.icon;
                  const active = settingsRouteActive && activeSettingsTab === subitem.tab;
                  return (
                    <li key={subitem.tab}>
                      <a
                        href={subitem.href}
                        onClick={closeMobileMenu}
                        className={[
                          'flex items-center gap-2 px-2.5 py-2 rounded-sm transition-all duration-200',
                          'font-sans text-[0.7rem] tracking-[0.06em]',
                          active
                            ? 'text-pe-rose-soft bg-pe-rose/8'
                            : 'text-pe-on-dark-muted hover:text-pe-on-dark hover:bg-pe-white/4',
                        ].join(' ')}
                        aria-current={active ? 'page' : undefined}
                      >
                        <SubIcon size={13} className={active ? 'text-pe-rose-soft' : 'text-pe-on-dark-muted'} />
                        <span>{subitem.label}</span>
                      </a>
                    </li>
                  );
                })}
              </ul>
            </div>
          </li>}
        </ul>
      </nav>

      <div className="border-t border-pe-white/8 p-3">
        {!isCollapsed && user && (
          <div className="px-2 py-1.5 mb-2">
            <p className="font-sans text-[0.65rem] text-pe-on-dark-muted truncate">{user.email}</p>
          </div>
        )}
        <button
          type="button"
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-sm text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors duration-150 font-sans text-[0.78rem]"
          title={isCollapsed ? 'Cerrar sesion' : undefined}
        >
          <LogOut size={16} className="flex-shrink-0" />
          {!isCollapsed && <span>Cerrar sesion</span>}
        </button>
      </div>
    </div>
  );
}
