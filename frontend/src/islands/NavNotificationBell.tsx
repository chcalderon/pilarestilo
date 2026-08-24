import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
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
  readonly locale: 'es' | 'en';
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

function bellLabelFor(unreadCount: number, es: boolean): string {
  if (unreadCount > 0) return es ? `${unreadCount} notificaciones sin leer` : `${unreadCount} unread notifications`;
  return es ? 'Notificaciones' : 'Notifications';
}

function themeFor(dark: boolean) {
  return {
    bg: dark ? '#1c1c1c' : '#ffffff',
    border: dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.12)',
    shadow: dark
      ? '0 12px 48px rgba(0,0,0,0.70), 0 2px 10px rgba(0,0,0,0.50)'
      : '0 12px 48px rgba(0,0,0,0.16), 0 2px 10px rgba(0,0,0,0.09)',
    divider: dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)',
    label: dark ? '#777' : '#aaa',
    text: dark ? '#d6d6d6' : '#333',
    subtext: dark ? '#888' : '#999',
    hoverBg: dark ? 'rgba(183,110,121,0.10)' : 'rgba(183,110,121,0.06)',
    closeClr: dark ? '#555' : '#bbb',
    closeHov: dark ? '#999' : '#666',
    linkClr: '#B76E79',
    linkHov: dark ? '#d4929d' : '#8B4A55',
    unreadDot: '#B76E79',
    unreadBg: dark ? 'rgba(183,110,121,0.08)' : 'rgba(183,110,121,0.04)',
  };
}

type Theme = ReturnType<typeof themeFor>;

interface ConfigAlert { readonly id: string; readonly message: string; readonly href: string; }

interface NotificationDropdownProps {
  readonly dropRef: React.RefObject<HTMLDivElement>;
  readonly pos: { top: number; right: number };
  readonly t: Theme;
  readonly es: boolean;
  readonly locale: 'es' | 'en';
  readonly unreadRecent: InAppNotificationDto[];
  readonly configAlerts: ConfigAlert[];
  readonly onNotifClick: (n: InAppNotificationDto) => void;
  readonly onClose: () => void;
}

function NotificationDropdown({ dropRef, pos, t, es, locale, unreadRecent, configAlerts, onNotifClick, onClose }: NotificationDropdownProps) {
  return (
    <div
      ref={dropRef}
      style={{ position: 'fixed', top: pos.top, right: pos.right, width: '320px', backgroundColor: t.bg, border: `1px solid ${t.border}`, boxShadow: t.shadow, zIndex: 9999 }}
    >
      <div style={{ borderBottom: `1px solid ${t.divider}`, padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.62rem', letterSpacing: '0.18em', textTransform: 'uppercase', color: t.label }}>
          {es ? 'Notificaciones' : 'Notifications'}
        </span>
        <button type="button" onClick={onClose} style={{ color: t.closeClr, background: 'none', border: 'none', cursor: 'pointer', padding: '2px', lineHeight: 0 }}
          onMouseEnter={e => (e.currentTarget.style.color = t.closeHov)} onMouseLeave={e => (e.currentTarget.style.color = t.closeClr)}>
          <X size={13} />
        </button>
      </div>

      {unreadRecent.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, maxHeight: '280px', overflowY: 'auto' }}>
          {unreadRecent.map(n => (
            <li key={n.id} style={{ borderBottom: `1px solid ${t.divider}`, backgroundColor: t.unreadBg }}>
              <button type="button" onClick={() => onNotifClick(n)}
                style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer', transition: 'background-color 150ms' }}
                onMouseEnter={e => (e.currentTarget.style.backgroundColor = t.hoverBg)}
                onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: t.unreadDot }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ margin: 0, fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.4', color: t.text, fontWeight: 500 }}>
                    {n.title}
                  </p>
                  <p style={{ margin: '2px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.66rem', color: t.subtext, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {n.body}
                  </p>
                  <p style={{ margin: '3px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.60rem', color: t.subtext }}>
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
          {unreadRecent.length > 0 && <div style={{ height: '1px', backgroundColor: t.divider, margin: '0 16px' }} />}
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {configAlerts.map(a => (
              <li key={a.id} style={{ borderBottom: `1px solid ${t.divider}` }}>
                <a href={a.href} onClick={onClose}
                  style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', transition: 'background-color 150ms' }}
                  onMouseEnter={e => (e.currentTarget.style.backgroundColor = t.hoverBg)}
                  onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                  <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#e6a817' }} />
                  <span style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.5', color: t.text }}>{a.message}</span>
                </a>
              </li>
            ))}
          </ul>
        </>
      )}

      <div style={{ padding: '10px 16px', borderTop: `1px solid ${t.divider}` }}>
        <a href={`/${locale}/account#notifications`} onClick={onClose}
          style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.62rem', letterSpacing: '0.16em', textTransform: 'uppercase', color: t.linkClr, textDecoration: 'none' }}
          onMouseEnter={e => (e.currentTarget.style.color = t.linkHov)}
          onMouseLeave={e => (e.currentTarget.style.color = t.linkClr)}>
          {es ? 'Ver historial →' : 'View history →'}
        </a>
      </div>
    </div>
  );
}

export default function NavNotificationBell({ locale }: Props) {
  const { user, token } = useAuthStore();
  const effectiveToken = useMemo(() => token ?? readAuthTokenCookie(), [token]);
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

  const fetchData = useCallback(async () => {
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
  }, [effectiveToken, user, es, locale]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 60_000);
    const onUpdated = () => { void fetchData(); };
    window.addEventListener('pe:notifications:updated', onUpdated);
    return () => {
      clearInterval(interval);
      window.removeEventListener('pe:notifications:updated', onUpdated);
    };
  }, [fetchData]);

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
  const t = themeFor(dark);
  const unreadRecent = recent.filter((n) => !n.read);

  const handleNotifClick = async (n: InAppNotificationDto) => {
    setOpen(false);
    if (!n.read && effectiveToken) {
      await markNotificationRead(n.id, effectiveToken).catch(() => {});
      setUnreadCount(c => Math.max(0, c - 1));
      setRecent(prev => prev.filter(x => x.id !== n.id));
    }
    const link = n.metadata?.link as string | undefined;
    if (link) window.location.href = link;
  };

  const dropdown = (
    <NotificationDropdown
      dropRef={dropRef}
      pos={dropdownPos}
      t={t}
      es={es}
      locale={locale}
      unreadRecent={unreadRecent}
      configAlerts={configAlerts}
      onNotifClick={handleNotifClick}
      onClose={() => setOpen(false)}
    />
  );

  const bellLabel = bellLabelFor(unreadCount, es);

  return (
    <div style={{ position: 'relative' }}>
      <button
        type="button"
        ref={btnRef}
        onClick={() => setOpen(v => !v)}
        className={`relative transition-colors duration-200 p-1 focus:outline-hidden ${dark ? 'text-pe-on-dark-muted hover:text-pe-rose-soft' : 'text-pe-muted hover:text-pe-rose-ink'}`}
        aria-label={bellLabel}
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-pe-rose rounded-full flex items-center justify-center shadow-xs">
            <span className="font-sans text-[0.5rem] font-bold text-white leading-none">{unreadCount > 9 ? '9+' : unreadCount}</span>
          </span>
        )}
      </button>
      {open && typeof document !== 'undefined' && createPortal(dropdown, document.body)}
    </div>
  );
}
