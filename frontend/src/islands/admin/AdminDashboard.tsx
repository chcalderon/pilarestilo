import { useState, useEffect } from 'react';
import { Package, CreditCard, Star } from 'lucide-react';
import { getProducts, getPendingPayments, getAdminReviews } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';

interface KpiCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accent?: string;
  loading?: boolean;
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
  const [loading, setLoading]             = useState(true);

  useEffect(() => {
    if (!effectiveToken) return;
    Promise.all([
      getProducts({ size: 1 }).then(p => setProductCount(p.totalElements)),
      getPendingPayments(effectiveToken).then(p => setPendingCount(p.length)),
      getAdminReviews(effectiveToken, false).then(r => setPendingReviews(r.length)),
    ]).finally(() => setLoading(false));
  }, [effectiveToken]);

  return (
    <div className="flex flex-col gap-8">
      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard
          label="Total productos"
          value={productCount ?? 0}
          icon={<Package size={20} />}
          loading={loading}
        />
        <KpiCard
          label="Pagos pendientes"
          value={pendingCount ?? 0}
          icon={<CreditCard size={20} />}
          loading={loading}
        />
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


