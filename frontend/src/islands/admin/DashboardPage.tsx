import { useEffect, useState } from "react";
import { AlertCircle } from "lucide-react";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from "recharts";
import { readAuthTokenCookie, useAuthStore } from "@/lib/authStore";

interface SalesTotal { amount: number; orderCount: number }
interface TopProduct { productId: string; name: string; unitsSold: number }
interface DailyRevenue { date: string; amount: number }
interface CajaSnapshot { status: string; openedAt: string; expectedBalance: number; saleCount: number; saleTotal: number }
interface LastSale { amount: number; recordedAt: string }
interface ExpiringWorker { userId: string; fullName: string; vigencyEnd: string }

interface AdminData {
  role: "ADMIN" | "SUPERVISOR";
  dailySales: SalesTotal;
  weeklySales: SalesTotal;
  openCashRegisters: number;
  pendingDispatches: number;
  inProgressDispatches: number;
  paymentsAwaitingReview?: number;
  topProducts: TopProduct[];
  dailyRevenueSeries: DailyRevenue[];
}

interface SellerData {
  role: "SELLER";
  currentCaja: CajaSnapshot | null;
  lastSale: LastSale | null;
}

interface DespachadorData {
  role: "DESPACHADOR";
  pendingDispatches: number;
  myDispatchedToday: number;
  myInProgress: number;
}

interface AdministracionData {
  role: "ADMINISTRACION";
  activeWorkers: number;
  paymentsAwaitingReview?: number;
  expiringWorkers: ExpiringWorker[];
}

type StatsData = AdminData | SellerData | DespachadorData | AdministracionData;

function formatCLP(amount: number) {
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP" }).format(amount);
}

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="border border-[var(--pe-border)] p-4 flex flex-col gap-1">
      <span className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">{label}</span>
      <span className="text-2xl font-bold font-[Cormorant_Garamond,serif]">{value}</span>
      {sub && <span className="text-xs text-[var(--pe-muted)]">{sub}</span>}
    </div>
  );
}

/**
 * The pending-review count, and a way straight to it.
 *
 * <p>A plain number would say money is waiting without offering to do anything about it, so with
 * anything pending the whole card becomes the link to the review queue and takes the alert
 * colour. At zero it stays a quiet stat: a permanent red badge reading 0 teaches people to
 * ignore red.
 */
function PaymentsAwaitingCard({ count }: { count: number }) {
  if (count === 0) {
    return <StatCard label="Pagos por revisar" value="0" sub="nada pendiente" />;
  }
  return (
    <a
      href="/admin/payments"
      className="border border-[var(--pe-rose)] bg-[var(--pe-rose)]/8 p-4 flex flex-col gap-1
        transition-colors hover:bg-[var(--pe-rose)]/15
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--pe-rose)]"
    >
      <span className="text-[10px] tracking-widest uppercase text-[var(--pe-rose)] flex items-center gap-1.5">
        <AlertCircle size={11} aria-hidden="true" />
        Pagos por revisar
      </span>
      <span className="text-2xl font-bold font-[Cormorant_Garamond,serif]">{count}</span>
      <span className="text-xs text-[var(--pe-muted)]">
        {count === 1 ? 'comprobante esperando' : 'comprobantes esperando'} · revisar
      </span>
    </a>
  );
}

/**
 * The shortcuts a role can actually use.
 *
 * <p>Filtered rather than shown-and-denied: a link that leads to a permission error teaches
 * nothing, and the sidebar already hides what a role cannot reach. Every role gets its own
 * short list instead of one list that is mostly wrong for everyone but the admin.
 */
const QUICK_ACTIONS: Record<string, Array<{ href: string; label: string; sub: string }>> = {
  ADMIN: [
    { href: '/admin/products', label: 'Gestionar productos', sub: 'Crear, editar, eliminar' },
    { href: '/admin/publicaciones', label: 'Publicaciones IA', sub: 'Lotes, assets y campañas n8n' },
    { href: '/admin/categories', label: 'Gestionar categorías', sub: 'Árbol de navegación' },
    { href: '/admin/reviews', label: 'Moderar reseñas', sub: 'Aprobar o rechazar' },
    { href: '/admin/payments', label: 'Revisar pagos', sub: 'Aprobar comprobantes' },
    { href: '/admin/users', label: 'Gestionar usuarios', sub: 'Clientes y trabajadores' },
  ],
  SUPERVISOR: [
    { href: '/admin/products', label: 'Gestionar productos', sub: 'Crear, editar, eliminar' },
    { href: '/admin/reviews', label: 'Moderar reseñas', sub: 'Aprobar o rechazar' },
    { href: '/admin/payments', label: 'Ver pagos', sub: 'Solo consulta' },
    { href: '/admin/despachos', label: 'Despachos', sub: 'Seguimiento de envíos' },
  ],
  ADMINISTRACION: [
    { href: '/admin/payments', label: 'Revisar pagos', sub: 'Aprobar comprobantes' },
    { href: '/admin/users', label: 'Gestionar usuarios', sub: 'Clientes y trabajadores' },
    { href: '/admin/caja', label: 'Caja', sub: 'Aperturas y cierres' },
  ],
  SELLER: [
    { href: '/admin/caja', label: 'Mi caja', sub: 'Abrir, cerrar, movimientos' },
    { href: '/admin/products', label: 'Ver productos', sub: 'Catálogo y stock' },
  ],
  DESPACHADOR: [
    { href: '/admin/despachos', label: 'Mis despachos', sub: 'Pendientes y en progreso' },
  ],
};

function QuickActions({ role }: { role: string }) {
  const links = QUICK_ACTIONS[role] ?? [];
  if (links.length === 0) return null;

  return (
    <div>
      <p className="text-[10px] tracking-[0.25em] uppercase text-[var(--pe-muted)] mb-4">
        Accesos rápidos
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {links.map(link => (
          <a
            key={link.href}
            href={link.href}
            className="block border border-[var(--pe-border)] p-4 transition-colors
              hover:border-[var(--pe-rose)]
              focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--pe-rose)]"
          >
            <p className="text-sm font-medium">{link.label}</p>
            <p className="text-xs text-[var(--pe-muted)] mt-0.5">{link.sub}</p>
          </a>
        ))}
      </div>
    </div>
  );
}

function SkeletonCard() {
  return (
    <div className="border border-[var(--pe-border)] p-4 animate-pulse">
      <div className="h-3 w-24 bg-[var(--pe-border)] rounded mb-2" />
      <div className="h-7 w-32 bg-[var(--pe-border)] rounded" />
    </div>
  );
}

function AdminDashboard({ data }: { data: AdminData }) {
  const chartData = data.dailyRevenueSeries.map(d => ({
    date: d.date.slice(5),
    amount: d.amount / 1000,
  }));

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="Ventas hoy" value={formatCLP(data.dailySales.amount)} sub={`${data.dailySales.orderCount} órdenes`} />
        <StatCard label="Ventas semana" value={formatCLP(data.weeklySales.amount)} sub={`${data.weeklySales.orderCount} órdenes`} />
        <StatCard label="Cajas abiertas" value={String(data.openCashRegisters)} />
        <StatCard label="Despachos pendientes" value={String(data.pendingDispatches)} sub={`${data.inProgressDispatches} en progreso`} />
        <PaymentsAwaitingCard count={data.paymentsAwaitingReview ?? 0} />
      </div>

      <div className="border border-[var(--pe-border)] p-4">
        <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-4">Ingresos últimos 7 días</p>
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--pe-border)" />
            <XAxis dataKey="date" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={v => `$${v}K`} tick={{ fontSize: 11 }} />
            <Tooltip formatter={(v) => `$${v}K`} labelFormatter={() => "Ingresos"} />
            <Bar dataKey="amount" fill="var(--pe-rose, #B76E79)" radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {data.topProducts.length > 0 && (
        <div className="border border-[var(--pe-border)] p-4">
          <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-3">Top productos (semana)</p>
          <ul className="space-y-2">
            {data.topProducts.map(p => (
              <li key={p.productId} className="flex justify-between text-sm">
                <span>{p.name}</span>
                <span className="font-medium">{p.unitsSold} uds.</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function SellerDashboard({ data }: { data: SellerData }) {
  const caja = data.currentCaja;
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      <StatCard
        label="Mi caja hoy"
        value={caja ? caja.status : "Sin caja"}
        sub={caja ? `Saldo esperado: ${formatCLP(caja.expectedBalance)}` : "Abre una caja para comenzar"}
      />
      {caja && (
        <StatCard
          label="Ventas en mi caja"
          value={formatCLP(caja.saleTotal)}
          sub={`${caja.saleCount} ventas`}
        />
      )}
      {data.lastSale && (
        <StatCard
          label="Última venta"
          value={formatCLP(data.lastSale.amount)}
          sub={new Date(data.lastSale.recordedAt).toLocaleTimeString("es-CL", { hour: "2-digit", minute: "2-digit" })}
        />
      )}
    </div>
  );
}

function DespachadorDashboard({ data }: { data: DespachadorData }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      <StatCard label="Pendientes" value={String(data.pendingDispatches)} />
      <StatCard label="En progreso (míos)" value={String(data.myInProgress)} />
      <StatCard label="Mis despachos hoy" value={String(data.myDispatchedToday)} />
    </div>
  );
}

function AdministracionDashboard({ data }: { data: AdministracionData }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <PaymentsAwaitingCard count={data.paymentsAwaitingReview ?? 0} />
        <StatCard label="Trabajadores activos" value={String(data.activeWorkers)} />
        <StatCard label="Vencimientos próximos" value={String(data.expiringWorkers.length)} sub="en los próximos 7 días" />
      </div>
      {data.expiringWorkers.length > 0 && (
        <div className="border border-[var(--pe-border)] p-4">
          <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-3">Vigencias por vencer</p>
          <ul className="space-y-2">
            {data.expiringWorkers.map(w => (
              <li key={w.userId} className="flex justify-between text-sm">
                <span>{w.fullName}</span>
                <span className="text-[var(--pe-rose)]">{w.vigencyEnd}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const [data, setData] = useState<StatsData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  useEffect(() => {
    if (!effectiveToken) {
      return;
    }

    let cancelled = false;

    async function load() {
      try {
        const res = await fetch("/api/dashboard/stats", {
          headers: { Authorization: `Bearer ${effectiveToken}` },
        });
        if (!res.ok) throw new Error(`Error ${res.status}`);
        const stats = await res.json() as StatsData;
        if (cancelled) return;
        setData(stats);
        setError(null);
      } catch {
        if (cancelled) return;
        setData(null);
        setError("No se pudo cargar el dashboard.");
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [effectiveToken]);

  if (error) {
    return <p className="text-sm text-red-500 p-4">{error}</p>;
  }

  if (!data) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-6">
        {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
      </div>
    );
  }

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold font-[Cormorant_Garamond,serif]">Dashboard</h1>
      {(data.role === "ADMIN" || data.role === "SUPERVISOR") && <AdminDashboard data={data as AdminData} />}
      {data.role === "SELLER" && <SellerDashboard data={data as SellerData} />}
      {data.role === "DESPACHADOR" && <DespachadorDashboard data={data as DespachadorData} />}
      {data.role === "ADMINISTRACION" && <AdministracionDashboard data={data as AdministracionData} />}
      {/* Figures first, then where to go about them. */}
      <QuickActions role={data.role} />
    </div>
  );
}
