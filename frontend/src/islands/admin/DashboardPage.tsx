import { useEffect, useState } from "react";
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
    </div>
  );
}
