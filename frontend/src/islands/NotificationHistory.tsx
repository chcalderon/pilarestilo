import { useState, useEffect, useCallback, useMemo } from 'react';
import { Bell } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type InAppNotificationDto,
  type Page,
} from '../lib/api';

function dispatchUpdated() {
  window.dispatchEvent(new CustomEvent('pe:notifications:updated'));
}

interface Props {
  readonly locale: 'es' | 'en';
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
      dispatchUpdated();
    } finally {
      setMarking(false);
    }
  };

  const handleMarkOne = async (n: InAppNotificationDto) => {
    if (n.read || !effectiveToken) return;
    setPage(prev => prev ? {
      ...prev,
      content: prev.content.map(x => x.id === n.id ? { ...x, read: true } : x),
    } : prev);
    await markNotificationRead(n.id, effectiveToken).catch(() => {});
    dispatchUpdated();
  };

  const allRead = page?.content.every((n) => n.read) ?? true;

  return (
    <div id="notifications" className="pt-8">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-display text-2xl text-pe-black font-light flex items-center gap-2">
          <Bell size={20} />
          {es ? 'Notificaciones' : 'Notifications'}
        </h2>
        {!allRead && (
          <button
            type="button"
            onClick={handleMarkAll}
            disabled={marking}
            className="font-sans text-xs text-pe-rose-ink hover:underline underline-offset-2 transition-colors disabled:opacity-50"
          >
            {es ? 'Marcar todas como leidas' : 'Mark all as read'}
          </button>
        )}
      </div>

      {loading && (
        <div className="font-sans text-sm text-pe-muted py-8 text-center">
          {es ? 'Cargando...' : 'Loading...'}
        </div>
      )}

      {!loading && page?.content.length === 0 && (
        <div className="font-sans text-sm text-pe-muted border border-pe-black/10 p-8 text-center">
          {es ? 'No tienes notificaciones aun.' : "You don't have any notifications yet."}
        </div>
      )}

      {!loading && page && page.content.length > 0 && (
        <div className="flex flex-col gap-0.5">
          {page.content.map((n) => (
            <div
              key={n.id}
              // Marking one as read is a real action, so it has to be reachable without a mouse.
              role={n.read ? undefined : 'button'}
              tabIndex={n.read ? undefined : 0}
              onClick={() => { void handleMarkOne(n); }}
              onKeyDown={(event) => {
                if (n.read) return;
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  void handleMarkOne(n);
                }
              }}
              className={`flex gap-4 p-4 border border-[var(--pe-border)] transition-colors duration-150 ${
                n.read ? 'cursor-default' : 'cursor-pointer bg-pe-rose/[0.07]'
              }`}
            >
              <div className="shrink-0 mt-0.5">
                {/* One of five per-type category hues; a data-driven colour, so it stays inline. */}
                <span
                  className="inline-block px-2 py-0.5 rounded-xs font-sans text-[0.6rem] tracking-[0.1em] uppercase font-semibold text-white"
                  style={{ backgroundColor: TYPE_COLORS[n.type] ?? '#A1505C' }}
                >
                  {labels[n.type] ?? n.type}
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <p className={`font-sans text-sm text-pe-black ${n.read ? 'font-normal' : 'font-semibold'}`}>
                  {n.title}
                </p>
                <p className="font-sans text-sm text-pe-muted mt-1">
                  {n.body}
                </p>
              </div>
              <div className="shrink-0 text-right flex flex-col items-end gap-1">
                <span className="font-sans text-pe-muted text-[0.65rem]">
                  {relativeTime(n.createdAt, es)}
                </span>
                {!n.read && (
                  <span className="font-sans text-pe-rose-ink text-[0.58rem] tracking-[0.08em] uppercase">
                    {es ? 'Marcar leída' : 'Mark read'}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {page && page.totalPages > 1 && (
        <div className="flex gap-2 justify-center mt-6">
          <button
            type="button"
            disabled={currentPage === 0}
            onClick={() => {
              void load(currentPage - 1);
            }}
            className="font-sans text-xs px-3 py-1 border border-pe-black/20 disabled:opacity-30 hover:border-pe-rose transition-colors"
          >
            {es ? 'Anterior' : 'Previous'}
          </button>
          <span className="font-sans text-xs self-center text-pe-muted">
            {currentPage + 1} / {page.totalPages}
          </span>
          <button
            type="button"
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
