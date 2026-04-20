import { useState, useEffect } from 'react';
import { User, Star, ShoppingBag, Trash2, Loader2 } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { getMyReviews, deleteReview, getMyOrders, type ReviewDto, type OrderDto } from '../../lib/api';

interface Props {
  locale: 'es' | 'en';
}

type Tab = 'profile' | 'reviews' | 'orders';

export default function AccountPage({ locale }: Props) {
  const { user, token, clearAuth } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tab, setTab] = useState<Tab>('profile');
  const [reviews, setReviews] = useState<ReviewDto[]>([]);
  const [orders, setOrders] = useState<OrderDto[]>([]);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [ready, setReady] = useState(false);
  const es = locale === 'es';

  useEffect(() => {
    setReady(true);
  }, []);

  useEffect(() => {
    if (tab !== 'reviews' || !effectiveToken) return;
    setLoadingReviews(true);
    getMyReviews(effectiveToken)
      .then((r) => setReviews(r))
      .finally(() => setLoadingReviews(false));
  }, [tab, effectiveToken]);

  useEffect(() => {
    if (tab !== 'orders' || !effectiveToken) return;
    setLoadingOrders(true);
    getMyOrders(effectiveToken, 0, 20)
      .then((page) => setOrders(page.content ?? []))
      .finally(() => setLoadingOrders(false));
  }, [tab, effectiveToken]);

  // Redirect if not logged in (after hydration)
  useEffect(() => {
    if (ready && !user) {
      window.location.href = `/${locale}/auth/login?redirect=/${locale}/account`;
    }
  }, [ready, user, locale]);

  if (!ready || !user) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 size={28} className="animate-spin text-pe-rose/60" />
      </div>
    );
  }

  async function handleDeleteReview(reviewId: string) {
    if (!effectiveToken) return;
    try {
      await deleteReview(reviewId, effectiveToken);
      setReviews((prev) => prev.filter((r) => r.id !== reviewId));
    } catch {
      // no-op
    }
  }

  function handleLogout() {
    clearAuth();
    document.cookie = 'pe_token=; path=/; max-age=0; SameSite=Lax';
    window.location.href = `/${locale}/`;
  }

  function formatMoney(amount: number, currency: string) {
    return new Intl.NumberFormat(es ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency: currency || 'CLP',
      maximumFractionDigits: 0,
    }).format(amount ?? 0);
  }

  function orderStatusLabel(status: OrderDto['status']) {
    const labelsEs: Record<OrderDto['status'], string> = {
      CREATED: 'Creado',
      PENDING_PAYMENT: 'Pendiente de pago',
      PAYMENT_UNDER_REVIEW: 'Pago en revision',
      PAID: 'Pagado',
      PREPARING_ORDER: 'Preparando pedido',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregado',
      CANCELLED: 'Cancelado',
    };
    const labelsEn: Record<OrderDto['status'], string> = {
      CREATED: 'Created',
      PENDING_PAYMENT: 'Pending payment',
      PAYMENT_UNDER_REVIEW: 'Payment under review',
      PAID: 'Paid',
      PREPARING_ORDER: 'Preparing order',
      SHIPPED: 'Shipped',
      DELIVERED: 'Delivered',
      CANCELLED: 'Cancelled',
    };
    return (es ? labelsEs : labelsEn)[status] ?? status;
  }

  function paymentMethodLabel(method: OrderDto['paymentMethod']) {
    const labelsEs: Record<OrderDto['paymentMethod'], string> = {
      BANK_TRANSFER: 'Transferencia',
      CASH_ON_DELIVERY: 'Contra entrega',
      AGREED_BY_WHATSAPP: 'Acordado por WhatsApp',
      STORE_CREDIT: 'Credito tienda',
      PAYMENT_GATEWAY: 'Pasarela de pago',
    };
    const labelsEn: Record<OrderDto['paymentMethod'], string> = {
      BANK_TRANSFER: 'Bank transfer',
      CASH_ON_DELIVERY: 'Cash on delivery',
      AGREED_BY_WHATSAPP: 'WhatsApp agreement',
      STORE_CREDIT: 'Store credit',
      PAYMENT_GATEWAY: 'Payment gateway',
    };
    return (es ? labelsEs : labelsEn)[method] ?? method;
  }

  const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'profile', label: es ? 'Perfil' : 'Profile', icon: <User size={14} /> },
    { id: 'reviews', label: es ? 'Mis resenas' : 'My reviews', icon: <Star size={14} /> },
    { id: 'orders', label: es ? 'Mis pedidos' : 'My orders', icon: <ShoppingBag size={14} /> },
  ];

  return (
    <div className="min-h-[calc(100vh-180px)] bg-pe-offwhite">
      <div className="bg-pe-cream border-b border-pe-black/6 py-10">
        <div className="pe-container flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <div>
            <p className="pe-eyebrow text-pe-charcoal/40 mb-1">{es ? 'Mi cuenta' : 'My account'}</p>
            <h1 className="font-display text-pe-black text-3xl font-light">{user.email}</h1>
            <span className="inline-block mt-1.5 font-sans text-[0.65rem] tracking-wider uppercase bg-pe-rose/12 text-pe-rose-deep px-2 py-0.5">
              {user.role === 'ADMIN' ? 'Admin' : user.role === 'SELLER' ? (es ? 'Vendedor/a' : 'Seller') : (es ? 'Cliente' : 'Customer')}
            </span>
          </div>
          <button
            onClick={handleLogout}
            className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/40 hover:text-pe-rose-deep transition-colors duration-200"
          >
            {es ? 'Cerrar sesion' : 'Sign out'}
          </button>
        </div>
      </div>

      <div className="pe-container py-10">
        <nav className="flex gap-0 border-b border-pe-black/10 mb-8">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-2 px-5 py-3 font-sans text-[0.72rem] tracking-[0.18em] uppercase transition-colors duration-200 border-b-2 -mb-px ${
                tab === t.id
                  ? 'border-pe-rose text-pe-rose-deep'
                  : 'border-transparent text-pe-charcoal/50 hover:text-pe-charcoal'
              }`}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </nav>

        {tab === 'profile' && (
          <div className="max-w-md flex flex-col gap-5">
            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">Email</p>
              <p className="font-sans text-pe-charcoal">{user.email}</p>
            </div>
            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Rol' : 'Role'}</p>
              <p className="font-sans text-pe-charcoal">
                {user.role === 'ADMIN' ? 'Admin' : user.role === 'SELLER' ? (es ? 'Vendedor/a' : 'Seller') : (es ? 'Cliente' : 'Customer')}
              </p>
            </div>
            <p className="font-sans text-[0.72rem] text-pe-charcoal/40">
              {es ? 'ID de cuenta: ' : 'Account ID: '}
              {user.id}
            </p>
          </div>
        )}

        {tab === 'reviews' && (
          <div className="max-w-2xl">
            {loadingReviews ? (
              <div className="flex justify-center py-16">
                <Loader2 size={24} className="animate-spin text-pe-rose/60" />
              </div>
            ) : reviews.length === 0 ? (
              <div className="text-center py-20">
                <Star size={32} className="text-pe-charcoal/20 mx-auto mb-3" />
                <p className="font-display text-pe-black/30 text-xl">{es ? 'Aun no escribiste resenas' : 'No reviews yet'}</p>
                <a
                  href={`/${locale}/products`}
                  className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-deep hover:underline underline-offset-2"
                >
                  {es ? 'Explorar productos' : 'Browse products'}
                </a>
              </div>
            ) : (
              <ul className="flex flex-col gap-4">
                {reviews.map((review) => (
                  <li key={review.id} className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-2">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex gap-0.5">
                        {Array.from({ length: 5 }).map((_, i) => (
                          <Star
                            key={i}
                            size={13}
                            className={i < review.rating ? 'text-pe-rose fill-pe-rose' : 'text-pe-charcoal/20'}
                          />
                        ))}
                      </div>
                      <button
                        onClick={() => handleDeleteReview(review.id)}
                        className="text-pe-charcoal/30 hover:text-pe-rose-deep transition-colors duration-200 p-0.5"
                        aria-label={es ? 'Eliminar resena' : 'Delete review'}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                    {review.title && <p className="font-display text-pe-black font-medium text-[1rem]">{review.title}</p>}
                    {review.comment && <p className="font-sans text-sm text-pe-charcoal/70 leading-relaxed">{review.comment}</p>}
                    <div className="flex items-center gap-3 mt-1">
                      <span
                        className={`font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5 ${
                          review.approved ? 'bg-green-50 text-green-700' : 'bg-pe-cream text-pe-charcoal/40'
                        }`}
                      >
                        {review.approved ? (es ? 'Aprobada' : 'Approved') : (es ? 'Pendiente' : 'Pending')}
                      </span>
                      <span className="font-sans text-[0.68rem] text-pe-charcoal/35">
                        {new Date(review.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US')}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {tab === 'orders' && (
          <div className="max-w-3xl">
            {loadingOrders ? (
              <div className="flex justify-center py-16">
                <Loader2 size={24} className="animate-spin text-pe-rose/60" />
              </div>
            ) : orders.length === 0 ? (
              <div className="text-center py-20">
                <ShoppingBag size={32} className="text-pe-charcoal/20 mx-auto mb-3" />
                <p className="font-display text-pe-black/30 text-xl">{es ? 'Aun no tienes pedidos' : 'No orders yet'}</p>
                <a
                  href={`/${locale}/products`}
                  className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-deep hover:underline underline-offset-2"
                >
                  {es ? 'Explorar productos' : 'Browse products'}
                </a>
              </div>
            ) : (
              <ul className="flex flex-col gap-4">
                {orders.map((order) => (
                  <li key={order.id} className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-3">
                    <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
                      <div>
                        <p className="font-sans text-[0.65rem] tracking-[0.16em] uppercase text-pe-charcoal/35">
                          {es ? 'Pedido' : 'Order'}
                        </p>
                        <p className="font-mono text-[0.82rem] text-pe-charcoal/65 mt-0.5">{order.id}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-charcoal/60">
                          {paymentMethodLabel(order.paymentMethod)}
                        </span>
                        <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-rose/10 text-pe-rose-deep">
                          {orderStatusLabel(order.status)}
                        </span>
                      </div>
                    </div>

                    <ul className="flex flex-col gap-1.5 border-t border-pe-black/7 pt-3">
                      {order.items.map((item) => (
                        <li key={item.id} className="flex items-center justify-between gap-3">
                          <span className="font-sans text-sm text-pe-charcoal/75">
                            {item.productName} x{item.quantity}
                          </span>
                          <span className="font-sans text-sm text-pe-charcoal/55">
                            {formatMoney(item.unitPrice.amount, item.unitPrice.currency)}
                          </span>
                        </li>
                      ))}
                    </ul>

                    <div className="border-t border-pe-black/7 pt-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                      <span className="font-sans text-[0.72rem] text-pe-charcoal/45">
                        {new Date(order.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US', {
                          day: '2-digit',
                          month: '2-digit',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </span>
                      <p className="font-display text-[1.05rem] text-pe-black">
                        {es ? 'Total: ' : 'Total: '}
                        {formatMoney(order.totalAmount.amount, order.totalAmount.currency)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
