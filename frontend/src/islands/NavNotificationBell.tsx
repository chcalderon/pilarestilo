import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Bell, X } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  getMyProfile,
  getRecentNotifications,
  getUnreadNotificationsCount,
  markNotificationRead,
  type InAppNotificationDto,
} from '../lib/api';

interface Props {
  locale: 'es' | 'en';
}

function getTheme() {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.getAttribute('data-theme') ?? 'dark';
}

function relativeTime(iso: string, es: boolean): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60) return es ? 'hace un momento' : 'just now';
  if (diff < 3600) return es ? `hace ${Math.floor(diff / 60)} min` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return es ? `hace ${Math.floor(diff / 3600)}h` : `${Math.floor(diff / 3600)}h ago`;
  return es ? `hace ${Math.floor(diff / 86400)}d` : `${Math.floor(diff / 86400)}d ago`;
}

export default function NavNotificationBell({ locale }: Props) {
  const { user, token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [recent, setRecent] = useState<InAppNotificationDto[]>([]);
  const [configAlerts, setConfigAlerts] = useState<{ id: string; message: string; href: string }[]>([]);
  const [theme, setTheme] = useState<string>('dark');
  const [dropdownPos, setDropdownPos] = useState({ top: 0, right: 0 });
  const btnRef = useRef<HTMLButtonElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);
  const es = locale === 'es';

  useEffect(() => {
    setTheme(getTheme());
    const obs = new MutationObserver(() => setTheme(getTheme()));
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => obs.disconnect();
  }, []);

  const fetchData = async () => {
    if (!effectiveToken || !user) return;
    try {
      const [countRes, recentRes, profile] = await Promise.all([
        getUnreadNotificationsCount(effectiveToken),
        getRecentNotifications(effectiveToken, 5),
        getMyProfile(effectiveToken),
      ]);
      setUnreadCount(countRes.count);
      setRecent(recentRes.content);

      const alerts: { id: string; message: string; href: string }[] = [];
      if (!profile.notificationChannelPreference || profile.notificationChannelPreference === 'AUTO') {
        alerts.push({
          id: 'notif-channel',
          message: es
            ? 'Configura tu canal de notificaciones para recibir actualizaciones de pedidos.'
            : 'Set up your notification channel to receive order updates.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      if (!profile.phone) {
        alerts.push({
          id: 'phone',
          message: es
            ? 'Agrega tu número de teléfono para comunicación más rápida.'
            : 'Add your phone number for faster communication.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      setConfigAlerts(alerts);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 60_000);
    return () => clearInterval(interval);
  }, [user?.id, effectiveToken]);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      const t = e.target as Node;
      if (btnRef.current && !btnRef.current.contains(t) && dropRef.current && !dropRef.current.contains(t)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  function calcPos() {
    if (!btnRef.current) return;
    const rect = btnRef.current.getBoundingClientRect();
    setDropdownPos({ top: rect.bottom + 10, right: window.innerWidth - rect.right });
  }

  useEffect(() => {
    if (!open) return;
    calcPos();
    window.addEventListener('scroll', calcPos, { passive: true });
    window.addEventListener('resize', calcPos, { passive: true });
    return () => { window.removeEventListener('scroll', calcPos); window.removeEventListener('resize', calcPos); };
  }, [open]);

  if (!user) return null;

  const dark = theme === 'dark';
  const bg = dark ? '#1c1c1c' : '#ffffff';
  const border = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.12)';
  const shadow = dark
    ? '0 12px 48px rgba(0,0,0,0.70), 0 2px 10px rgba(0,0,0,0.50)'
    : '0 12px 48px rgba(0,0,0,0.16), 0 2px 10px rgba(0,0,0,0.09)';
  const divider = dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)';
  const label = dark ? '#777' : '#aaa';
  const text = dark ? '#d6d6d6' : '#333';
  const subtext = dark ? '#888' : '#999';
  const hoverBg = dark ? 'rgba(183,110,121,0.10)' : 'rgba(183,110,121,0.06)';
  const closeClr = dark ? '#555' : '#bbb';
  const closeHov = dark ? '#999' : '#666';
  const linkClr = '#B76E79';
  const linkHov = dark ? '#d4929d' : '#8B4A55';
  const unreadDot = '#B76E79';
  const unreadBg = dark ? 'rgba(183,110,121,0.08)' : 'rgba(183,110,121,0.04)';

  const handleNotifClick = async (n: InAppNotificationDto) => {
    setOpen(false);
    if (!n.read && effectiveToken) {
      await markNotificationRead(n.id, effectiveToken).catch(() => {});
      setUnreadCount(c => Math.max(0, c - 1));
    }
    const link = n.metadata?.link as string | undefined;
    if (link) window.location.href = link;
  };

  const dropdown = (
    <div
      ref={dropRef}
      style={{ position: 'fixed', top: dropdownPos.top, right: dropdownPos.right, width: '320px', backgroundColor: bg, border: `1px solid ${border}`, boxShadow: shadow, zIndex: 9999 }}
    >
      <div style={{ borderBottom: `1px solid ${divider}`, padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.62rem', letterSpacing: '0.18em', textTransform: 'uppercase', color: label }}>
          {es ? 'Notificaciones' : 'Notifications'}
        </span>
        <button onClick={() => setOpen(false)} style={{ color: closeClr, background: 'none', border: 'none', cursor: 'pointer', padding: '2px', lineHeight: 0 }}
          onMouseEnter={e => (e.currentTarget.style.color = closeHov)} onMouseLeave={e => (e.currentTarget.style.color = closeClr)}>
          <X size={13} />
        </button>
      </div>

      {recent.length === 0 && configAlerts.length === 0 && (
        <div style={{ padding: '20px 16px', textAlign: 'center', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', color: subtext }}>
          {es ? 'Sin notificaciones' : 'No notifications'}
        </div>
      )}

      {recent.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, maxHeight: '280px', overflowY: 'auto' }}>
          {recent.map(n => (
            <li key={n.id} style={{ borderBottom: `1px solid ${divider}`, backgroundColor: n.read ? 'transparent' : unreadBg }}>
              <button onClick={() => handleNotifClick(n)}
                style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer', transition: 'background-color 150ms' }}
                onMouseEnter={e => (e.currentTarget.style.backgroundColor = hoverBg)}
                onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: n.read ? 'transparent' : unreadDot, border: n.read ? `1px solid ${subtext}` : 'none' }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ margin: 0, fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.4', color: text, fontWeight: n.read ? 400 : 500 }}>
                    {n.title}
                  </p>
                  <p style={{ margin: '2px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.66rem', color: subtext, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {n.body}
                  </p>
                  <p style={{ margin: '3px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.60rem', color: subtext }}>
                    {relativeTime(n.createdAt, es)}
                  </p>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {configAlerts.length > 0 && (
        <>
          {recent.length > 0 && <div style={{ height: '1px', backgroundColor: divider, margin: '0 16px' }} />}
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {configAlerts.map(a => (
              <li key={a.id} style={{ borderBottom: `1px solid ${divider}` }}>
                <a href={a.href} onClick={() => setOpen(false)}
                  style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', transition: 'background-color 150ms' }}
                  onMouseEnter={e => (e.currentTarget.style.backgroundColor = hoverBg)}
                  onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                  <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#e6a817' }} />
                  <span style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.5', color: text }}>{a.message}</span>
                </a>
              </li>
            ))}
          </ul>
        </>
      )}

      <div style={{ padding: '10px 16px', borderTop: `1px solid ${divider}` }}>
        <a href={`/${locale}/account#notifications`} onClick={() => setOpen(false)}
          style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.62rem', letterSpacing: '0.16em', textTransform: 'uppercase', color: linkClr, textDecoration: 'none' }}
          onMouseEnter={e => (e.currentTarget.style.color = linkHov)}
          onMouseLeave={e => (e.currentTarget.style.color = linkClr)}>
          {es ? 'Ver historial →' : 'View history →'}
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
        aria-label={unreadCount > 0 ? (es ? `${unreadCount} notificaciones sin leer` : `${unreadCount} unread notifications`) : (es ? 'Notificaciones' : 'Notifications')}
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-pe-rose rounded-full flex items-center justify-center shadow-sm">
            <span className="font-sans text-[0.5rem] font-bold text-white leading-none">{unreadCount > 9 ? '9+' : unreadCount}</span>
          </span>
        )}
      </button>
      {open && typeof document !== 'undefined' && createPortal(dropdown, document.body)}
    </div>
  );
}
