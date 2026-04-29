import { useState, useEffect, useCallback, useMemo } from 'react';
import { Bell } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  getNotifications,
  markAllNotificationsRead,
  type InAppNotificationDto,
  type Page,
} from '../lib/api';

interface Props {
  locale: 'es' | 'en';
}

const TYPE_COLORS: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: '#16a34a',
  ORDER_CONFIRMED: '#2563eb',
  PAYMENT_RECEIVED: '#7c3aed',
  ORDER_PREPARING: '#b45309',
  ORDER_SHIPPED: '#ea580c',
};

const TYPE_LABELS_ES: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: 'Descuento',
  ORDER_CONFIRMED: 'Pedido',
  PAYMENT_RECEIVED: 'Pago',
  ORDER_PREPARING: 'Preparacion',
  ORDER_SHIPPED: 'Envio',
};

const TYPE_LABELS_EN: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: 'Discount',
  ORDER_CONFIRMED: 'Order',
  PAYMENT_RECEIVED: 'Payment',
  ORDER_PREPARING: 'Preparing',
  ORDER_SHIPPED: 'Shipping',
};

function relativeTime(iso: string, es: boolean): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60) return es ? 'hace un momento' : 'just now';
  if (diff < 3600) return es ? `hace ${Math.floor(diff / 60)} min` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return es ? `hace ${Math.floor(diff / 3600)}h` : `${Math.floor(diff / 3600)}h ago`;
  const days = Math.floor(diff / 86400);
  return es ? `hace ${days}d` : `${days}d ago`;
}

export default function NotificationHistory({ locale }: Props) {
  const { token } = useAuthStore();
  const effectiveToken = useMemo(() => token ?? readAuthTokenCookie(), [token]);
  const [page, setPage] = useState<Page<InAppNotificationDto> | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [marking, setMarking] = useState(false);
  const es = locale === 'es';
  const labels = es ? TYPE_LABELS_ES : TYPE_LABELS_EN;

  const load = useCallback(async (p: number) => {
    if (!effectiveToken) return;
    setLoading(true);
    try {
      const result = await getNotifications(effectiveToken, p, 20);
      setPage(result);
      setCurrentPage(p);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, [effectiveToken]);

  useEffect(() => {
    void load(0);
  }, [load]);

  const handleMarkAll = async () => {
    if (!effectiveToken) return;
    setMarking(true);
    try {
      await markAllNotificationsRead(effectiveToken);
      await load(currentPage);
    } finally {
      setMarking(false);
    }
  };

  const allRead = page?.content.every((n) => n.read) ?? true;

  return (
    <div id="notifications" style={{ paddingTop: '2rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <h2 className="font-display text-2xl text-pe-black font-light" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Bell size={20} />
          {es ? 'Notificaciones' : 'Notifications'}
        </h2>
        {!allRead && (
          <button
            onClick={handleMarkAll}
            disabled={marking}
            className="font-sans text-xs text-pe-rose hover:text-pe-rose-deep transition-colors disabled:opacity-50"
          >
            {es ? 'Marcar todas como leidas' : 'Mark all as read'}
          </button>
        )}
      </div>

      {loading && (
        <div className="font-sans text-sm text-pe-charcoal/70" style={{ padding: '2rem 0', textAlign: 'center' }}>
          {es ? 'Cargando...' : 'Loading...'}
        </div>
      )}

      {!loading && page && page.content.length === 0 && (
        <div className="font-sans text-sm text-pe-charcoal/70 border border-pe-black/10 p-8 text-center">
          {es ? 'No tienes notificaciones aun.' : "You don't have any notifications yet."}
        </div>
      )}

      {!loading && page && page.content.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {page.content.map((n) => (
            <div
              key={n.id}
              style={{
                display: 'flex',
                gap: '1rem',
                padding: '1rem',
                backgroundColor: n.read ? 'transparent' : 'rgba(183,110,121,0.07)',
                border: '1px solid var(--pe-border)',
                borderLeft: n.read ? '3px solid transparent' : `3px solid ${TYPE_COLORS[n.type] ?? '#B76E79'}`,
              }}
            >
              <div style={{ flexShrink: 0, marginTop: '2px' }}>
                <span style={{
                  display: 'inline-block',
                  padding: '2px 8px',
                  borderRadius: '2px',
                  backgroundColor: TYPE_COLORS[n.type] ?? '#B76E79',
                  color: '#fff',
                  fontSize: '0.60rem',
                  fontFamily: 'var(--font-sans,sans-serif)',
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                  fontWeight: 600,
                }}>
                  {labels[n.type] ?? n.type}
                </span>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p className="font-sans text-sm text-pe-black" style={{ margin: 0, fontWeight: n.read ? 400 : 600 }}>
                  {n.title}
                </p>
                <p className="font-sans text-sm text-pe-charcoal/70" style={{ margin: '4px 0 0' }}>
                  {n.body}
                </p>
              </div>
              <div style={{ flexShrink: 0, textAlign: 'right' }}>
                <span className="font-sans text-pe-charcoal/60" style={{ fontSize: '0.65rem' }}>
                  {relativeTime(n.createdAt, es)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {page && page.totalPages > 1 && (
        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginTop: '1.5rem' }}>
          <button
            disabled={currentPage === 0}
            onClick={() => {
              void load(currentPage - 1);
            }}
            className="font-sans text-xs px-3 py-1 border border-pe-black/20 disabled:opacity-30 hover:border-pe-rose transition-colors"
          >
            {es ? 'Anterior' : 'Previous'}
          </button>
          <span className="font-sans text-xs self-center text-pe-charcoal/60">
            {currentPage + 1} / {page.totalPages}
          </span>
          <button
            disabled={currentPage >= page.totalPages - 1}
            onClick={() => {
              void load(currentPage + 1);
            }}
            className="font-sans text-xs px-3 py-1 border border-pe-black/20 disabled:opacity-30 hover:border-pe-rose transition-colors"
          >
            {es ? 'Siguiente' : 'Next'}
          </button>
        </div>
      )}
    </div>
  );
}
