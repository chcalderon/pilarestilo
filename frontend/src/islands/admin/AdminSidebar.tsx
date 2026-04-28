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
  DollarSign,
} from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';

interface Props {
  currentPath: string;
  mobile?: boolean;
}

type SettingsSubmenuTab = 'store' | 'payments' | 'media' | 'notifications';

const navItems: Array<{ href: string; icon: typeof LayoutDashboard; label: string; viewKey: string }> = [
  { href: '/admin/', icon: LayoutDashboard, label: 'Dashboard', viewKey: 'dashboard' },
  { href: '/admin/products', icon: Package, label: 'Productos', viewKey: 'productos' },
  { href: '/admin/categories', icon: Tag, label: 'Categorias', viewKey: 'productos' },
  { href: '/admin/reviews', icon: Star, label: 'Resenas', viewKey: 'productos' },
  { href: '/admin/payments', icon: CreditCard, label: 'Pagos', viewKey: 'caja' },
  { href: '/admin/caja', icon: DollarSign, label: 'Caja', viewKey: 'caja' },
  { href: '/admin/discounts', icon: Ticket, label: 'Descuentos', viewKey: 'productos' },
  { href: '/admin/users', icon: Users, label: 'Usuarios', viewKey: 'usuarios' },
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
  { href: '/admin/settings?tab=media', tab: 'media', icon: Image, label: 'Media' },
  { href: '/admin/settings?tab=notifications', tab: 'notifications', icon: Bell, label: 'Notificaciones' },
];

export default function AdminSidebar({ currentPath, mobile = false }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [settingsExpanded, setSettingsExpanded] = useState(currentPath.startsWith('/admin/settings'));
  const [activeSettingsTab, setActiveSettingsTab] = useState<SettingsSubmenuTab>('store');
  const { user, clearAuth } = useAuthStore();

  const permissions = user?.permissions ?? [];
  const visibleNavItems = user?.role === 'ADMIN'
    ? navItems
    : navItems.filter(item => permissions.includes(item.viewKey));
  const showSettings = user?.role === 'ADMIN' || permissions.includes('configuracion');

  const isCollapsed = mobile ? false : collapsed;
  const settingsRouteActive = currentPath.startsWith('/admin/settings');
  const showSettingsChildren = !isCollapsed && settingsExpanded;
  const settingsSubmenuId = mobile ? 'admin-settings-submenu-mobile' : 'admin-settings-submenu-desktop';

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const tab = params.get('tab');
    if (tab === 'payments' || tab === 'media' || tab === 'notifications' || tab === 'store') {
      setActiveSettingsTab(tab);
      return;
    }
    setActiveSettingsTab('store');
  }, []);

  function closeMobileMenu() {
    if (!mobile) return;
    document.body.classList.remove('admin-mobile-open');
  }

  function handleLogout() {
    clearAuth();
    document.cookie = 'pe_token=; path=/; max-age=0; SameSite=Lax';
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

  return (
    <div
      className="flex flex-col h-full bg-pe-black text-pe-offwhite transition-all duration-300"
      style={{ width: mobile ? '100%' : isCollapsed ? '60px' : '240px' }}
    >
      <div className="flex items-center justify-between px-4 py-4 border-b border-pe-white/8 min-h-[64px]">
        <div className="flex items-center gap-2 overflow-hidden">
          <img src="/logo-pe.svg" alt="" width="28" height="28" aria-hidden="true" />
          {!isCollapsed && (
            <div className="flex flex-col leading-none">
              <span className="font-sans text-[0.58rem] tracking-[0.25em] uppercase text-pe-white/40">Pilar Estilo</span>
              <span className="font-sans text-[0.58rem] tracking-[0.2em] uppercase text-pe-rose/60">Admin</span>
            </div>
          )}
        </div>

        {!mobile && (
          <button
            onClick={() => setCollapsed((v) => !v)}
            className="ml-auto p-1 text-pe-white/30 hover:text-pe-rose-soft transition-colors rounded"
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
                    'flex items-center gap-3 px-3 py-2.5 rounded transition-colors duration-150 group',
                    'font-sans text-[0.78rem] tracking-[0.04em]',
                    active
                      ? 'bg-pe-rose/12 text-pe-rose-soft border-l-2 border-pe-rose pl-[10px]'
                      : 'text-pe-white/50 hover:text-pe-white/80 hover:bg-pe-white/4 border-l-2 border-transparent',
                  ].join(' ')}
                  aria-current={active ? 'page' : undefined}
                  title={isCollapsed ? item.label : undefined}
                >
                  <Icon size={16} className="flex-shrink-0" />
                  {!isCollapsed && <span>{item.label}</span>}
                </a>
              </li>
            );
          })}

          {showSettings && <li>
            <button
              type="button"
              onClick={handleSettingsToggle}
              className={[
                'w-full flex items-center gap-3 px-3 py-2.5 rounded transition-all duration-200 group border-l-2',
                'font-sans text-[0.78rem] tracking-[0.04em]',
                settingsRouteActive
                  ? 'bg-pe-rose/12 text-pe-rose-soft border-pe-rose'
                  : 'text-pe-white/50 hover:text-pe-white/80 hover:bg-pe-white/4 border-transparent',
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
                    showSettingsChildren ? 'rotate-90 text-pe-rose-soft' : 'text-pe-white/35',
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
                          'flex items-center gap-2 px-2.5 py-2 rounded border-l transition-all duration-200',
                          'font-sans text-[0.7rem] tracking-[0.06em]',
                          active
                            ? 'border-pe-rose text-pe-rose-soft bg-pe-rose/8'
                            : 'border-transparent text-pe-white/45 hover:text-pe-white/80 hover:bg-pe-white/4',
                        ].join(' ')}
                        aria-current={active ? 'page' : undefined}
                      >
                        <SubIcon size={13} className={active ? 'text-pe-rose-soft' : 'text-pe-white/40'} />
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
            <p className="font-sans text-[0.65rem] text-pe-white/25 truncate">{user.email}</p>
          </div>
        )}
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded text-pe-white/40 hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors duration-150 font-sans text-[0.78rem]"
          title={isCollapsed ? 'Cerrar sesion' : undefined}
        >
          <LogOut size={16} className="flex-shrink-0" />
          {!isCollapsed && <span>Cerrar sesion</span>}
        </button>
      </div>
    </div>
  );
}
