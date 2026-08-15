import { useState, useEffect } from 'react';
import { Package, CreditCard, FileCheck, Star } from 'lucide-react';
import { getProducts, getPendingPayments, getAdminReviews, getDashboardStats } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';

interface KpiCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accent?: string;
  loading?: boolean;
}

/**
 * Receipts waiting on a person, and a way straight to them.
 *
 * <p>A link rather than a figure when there is work: money sitting unjudged is something to act
 * on. At zero it stays a quiet card — a permanent alert reading 0 is how people learn to ignore
 * the colour.
 */
function PaymentsToReviewCard({ count, loading }: { count: number; loading: boolean }) {
  const label = 'Por revisar';
  if (loading || count === 0) {
    return <KpiCard label={label} value={count} icon={<FileCheck size={20} />} loading={loading} />;
  }
  return (
    <a
      href="/admin/payments"
      className="bg-[#fff6f7] border border-[#cb6070]/45 p-5 flex items-start gap-4
        transition-colors hover:bg-[#ffe9ec]
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose"
    >
      <div className="p-2.5 bg-[#8f2d3b] text-white flex-shrink-0">
        <FileCheck size={20} />
      </div>
      <div className="flex flex-col gap-0.5 min-w-0">
        <p className="font-sans text-[0.65rem] tracking-[0.18em] uppercase text-[#8f2d3b]">{label}</p>
        <p className="font-display text-pe-black text-2xl font-light leading-none">{count}</p>
        <p className="font-sans text-[0.68rem] text-[#732731] mt-0.5">
          {count === 1 ? 'comprobante esperando' : 'comprobantes esperando'}
        </p>
      </div>
    </a>
  );
}

function KpiCard({ label, value, icon, loading }: KpiCardProps) {
  return (
    <div className="bg-pe-white border border-pe-black/6 p-5 flex items-start gap-4">
      <div className="p-2.5 bg-pe-rose/8 text-pe-rose flex-shrink-0">
        {icon}
      </div>
      <div className="flex flex-col gap-0.5 min-w-0">
        <p className="font-sans text-[0.65rem] tracking-[0.18em] uppercase text-pe-charcoal/45">{label}</p>
        {loading
          ? <div className="h-7 w-16 bg-pe-cream animate-pulse rounded" />
          : <p className="font-display text-pe-black text-2xl font-light leading-none">{value}</p>
        }
      </div>
    </div>
  );
}

export default function AdminDashboard() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [productCount, setProductCount]   = useState<number | null>(null);
  const [pendingCount, setPendingCount]   = useState<number | null>(null);
  const [pendingReviews, setPendingReviews] = useState<number | null>(null);
  /** Receipts uploaded and not yet judged — the only figure here that waits on staff. */
  const [awaitingReview, setAwaitingReview] = useState<number | null>(null);
  const [loading, setLoading]             = useState(true);

  useEffect(() => {
    if (!effectiveToken) return;
    Promise.all([
      getProducts({ size: 1 }).then(p => setProductCount(p.totalElements)),
      getPendingPayments(effectiveToken).then(p => setPendingCount(p.length)),
      getAdminReviews(effectiveToken, false).then(r => setPendingReviews(r.length)),
      getDashboardStats(effectiveToken)
        .then(d => setAwaitingReview(d?.paymentsAwaitingReview ?? 0))
        .catch(() => setAwaitingReview(0)),
    ]).finally(() => setLoading(false));
  }, [effectiveToken]);

  return (
    <div className="flex flex-col gap-8">
      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Total productos"
          value={productCount ?? 0}
          icon={<Package size={20} />}
          loading={loading}
        />
        {/*
          * Two different waits, and they were being shown as one. PENDING means the customer has
          * not transferred yet — nothing for the team to do. SUBMITTED means a receipt is sitting
          * there unjudged, which is the only one that needs a person. Labelling the first
          * "Pagos pendientes" hid the second entirely.
          */}
        <KpiCard
          label="Esperando transferencia"
          value={pendingCount ?? 0}
          icon={<CreditCard size={20} />}
          loading={loading}
        />
        <PaymentsToReviewCard count={awaitingReview ?? 0} loading={loading} />
        <KpiCard
          label="Reseñas por moderar"
          value={pendingReviews ?? 0}
          icon={<Star size={20} />}
          loading={loading}
        />
      </div>

      {/* Quick links */}
      <div>
        <p className="font-sans text-[0.65rem] tracking-[0.25em] uppercase text-pe-charcoal/35 mb-4">
          Accesos rápidos
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
          {[
            { href: '/admin/products',   label: 'Gestionar productos',  sub: 'Crear, editar, eliminar' },
            { href: '/admin/publicaciones', label: 'Publicaciones IA', sub: 'Lotes, assets y campanas n8n' },
            { href: '/admin/categories', label: 'Gestionar categorías', sub: 'Árbol de navegación' },
            { href: '/admin/reviews',    label: 'Moderar reseñas',      sub: 'Aprobar o rechazar' },
            { href: '/admin/payments',   label: 'Revisar pagos',        sub: 'Aprobar comprobantes' },
            { href: '/admin/users',      label: 'Gestionar usuarios',   sub: 'Clientes y trabajadores' },
          ].map(link => (
            <a
              key={link.href}
              href={link.href}
              className="block bg-pe-white border border-pe-black/6 p-4 hover:border-pe-rose/40 hover:shadow-sm transition-all duration-200 group"
            >
              <p className="font-sans text-[0.82rem] font-medium text-pe-charcoal group-hover:text-pe-rose-deep transition-colors duration-200">
                {link.label}
              </p>
              <p className="font-sans text-[0.72rem] text-pe-charcoal/40 mt-0.5">{link.sub}</p>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}


