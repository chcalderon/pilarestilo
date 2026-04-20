import { useState } from 'react';
import {
  LayoutDashboard,
  Package,
  Tag,
  Star,
  CreditCard,
  Users,
  LogOut,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';

interface Props {
  currentPath: string;
  mobile?: boolean;
}

const navItems = [
  { href: '/admin/', icon: LayoutDashboard, label: 'Dashboard' },
  { href: '/admin/products', icon: Package, label: 'Productos' },
  { href: '/admin/categories', icon: Tag, label: 'Categorias' },
  { href: '/admin/reviews', icon: Star, label: 'Resenas' },
  { href: '/admin/payments', icon: CreditCard, label: 'Pagos' },
  { href: '/admin/users', icon: Users, label: 'Usuarios' },
];

export default function AdminSidebar({ currentPath, mobile = false }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const { user, clearAuth } = useAuthStore();

  const isCollapsed = mobile ? false : collapsed;

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
          {navItems.map((item) => {
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
