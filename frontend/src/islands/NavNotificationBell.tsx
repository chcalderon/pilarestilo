import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Bell, X } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import { getMyProfile } from '../lib/api';

interface Props {
  locale: 'es' | 'en';
}

interface Notification {
  id: string;
  message: string;
  href: string;
}

function getTheme() {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.getAttribute('data-theme') ?? 'dark';
}

export default function NavNotificationBell({ locale }: Props) {
  const { user, token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [ready, setReady] = useState(false);
  const [theme, setTheme] = useState<string>('dark');
  const [dropdownPos, setDropdownPos] = useState({ top: 0, right: 0 });
  const btnRef = useRef<HTMLButtonElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);
  const es = locale === 'es';

  // Track theme
  useEffect(() => {
    setTheme(getTheme());
    const obs = new MutationObserver(() => setTheme(getTheme()));
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => obs.disconnect();
  }, []);

  // Fetch notifications
  useEffect(() => {
    if (!effectiveToken || !user) { setReady(true); return; }
    getMyProfile(effectiveToken).then(profile => {
      const items: Notification[] = [];
      if (!profile.notificationChannelPreference || profile.notificationChannelPreference === 'AUTO') {
        items.push({
          id: 'notif-channel',
          message: es
            ? 'Configura tu canal de notificaciones para recibir actualizaciones de pedidos.'
            : 'Set up your notification channel to receive order updates.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      if (!profile.phone) {
        items.push({
          id: 'phone',
          message: es
            ? 'Agrega tu número de teléfono para comunicación más rápida.'
            : 'Add your phone number for faster communication.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      setNotifications(items);
      setReady(true);
    }).catch(() => { setReady(true); });
  }, [user?.id, effectiveToken]);

  // Outside click
  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      const target = e.target as Node;
      if (
        btnRef.current && !btnRef.current.contains(target) &&
        dropRef.current && !dropRef.current.contains(target)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  // Calculate fixed position from button
  function calcPos() {
    if (!btnRef.current) return;
    const rect = btnRef.current.getBoundingClientRect();
    setDropdownPos({
      top: rect.bottom + 10,
      right: window.innerWidth - rect.right,
    });
  }

  useEffect(() => {
    if (!open) return;
    calcPos();
    window.addEventListener('scroll', calcPos, { passive: true });
    window.addEventListener('resize', calcPos, { passive: true });
    return () => {
      window.removeEventListener('scroll', calcPos);
      window.removeEventListener('resize', calcPos);
    };
  }, [open]);

  if (!ready || !user || notifications.length === 0) return null;

  const count = notifications.length;
  const dark = theme === 'dark';

  const bg        = dark ? '#1c1c1c' : '#ffffff';
  const border    = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.12)';
  const shadow    = dark
    ? '0 12px 48px rgba(0,0,0,0.70), 0 2px 10px rgba(0,0,0,0.50)'
    : '0 12px 48px rgba(0,0,0,0.16), 0 2px 10px rgba(0,0,0,0.09)';
  const divider   = dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)';
  const label     = dark ? '#777' : '#aaa';
  const text      = dark ? '#d6d6d6' : '#333';
  const hoverBg   = dark ? 'rgba(183,110,121,0.10)' : 'rgba(183,110,121,0.06)';
  const closeClr  = dark ? '#555' : '#bbb';
  const closeHov  = dark ? '#999' : '#666';
  const linkClr   = '#B76E79';
  const linkHov   = dark ? '#d4929d' : '#8B4A55';

  const dropdown = (
    <div
      ref={dropRef}
      style={{
        position: 'fixed',
        top: dropdownPos.top,
        right: dropdownPos.right,
        width: '300px',
        backgroundColor: bg,
        border: `1px solid ${border}`,
        boxShadow: shadow,
        zIndex: 9999,
      }}
    >
      {/* Header */}
      <div style={{ borderBottom: `1px solid ${divider}`, padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.62rem', letterSpacing: '0.18em', textTransform: 'uppercase', color: label }}>
          {es ? 'Notificaciones' : 'Notifications'}
        </span>
        <button
          onClick={() => setOpen(false)}
          style={{ color: closeClr, background: 'none', border: 'none', cursor: 'pointer', padding: '2px', lineHeight: 0 }}
          onMouseEnter={e => (e.currentTarget.style.color = closeHov)}
          onMouseLeave={e => (e.currentTarget.style.color = closeClr)}
        >
          <X size={13} />
        </button>
      </div>

      {/* Items */}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {notifications.map(n => (
          <li key={n.id} style={{ borderBottom: `1px solid ${divider}` }}>
            <a
              href={n.href}
              onClick={() => setOpen(false)}
              style={{ display: 'flex', gap: '12px', padding: '14px 16px', textDecoration: 'none', backgroundColor: 'transparent', transition: 'background-color 150ms' }}
              onMouseEnter={e => (e.currentTarget.style.backgroundColor = hoverBg)}
              onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <span style={{ marginTop: '6px', flexShrink: 0, width: '6px', height: '6px', minWidth: '6px', minHeight: '6px', borderRadius: '50%', backgroundColor: '#B76E79' }} />
              <span style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.74rem', lineHeight: '1.5', color: text }}>
                {n.message}
              </span>
            </a>
          </li>
        ))}
      </ul>

      {/* Footer */}
      <div style={{ padding: '10px 16px' }}>
        <a
          href={`/${locale}/account?tab=profile`}
          onClick={() => setOpen(false)}
          style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.62rem', letterSpacing: '0.16em', textTransform: 'uppercase', color: linkClr, textDecoration: 'none' }}
          onMouseEnter={e => (e.currentTarget.style.color = linkHov)}
          onMouseLeave={e => (e.currentTarget.style.color = linkClr)}
        >
          {es ? 'Ir a mi perfil →' : 'Go to my profile →'}
        </a>
      </div>
    </div>
  );

  return (
    <div style={{ position: 'relative' }}>
      <button
        ref={btnRef}
        onClick={() => setOpen(v => !v)}
        className="relative text-pe-white/40 hover:text-pe-rose-soft transition-colors duration-200 p-1 focus:outline-none"
        aria-label={es ? `${count} notificaciones` : `${count} notifications`}
      >
        <Bell size={18} />
        <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-pe-rose rounded-full flex items-center justify-center shadow-sm">
          <span className="font-sans text-[0.5rem] font-bold text-white leading-none">{count}</span>
        </span>
      </button>

      {open && typeof document !== 'undefined' && createPortal(dropdown, document.body)}
    </div>
  );
}
