import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

interface Movement {
  id: string;
  type: string;
  amount: number;
  description: string;
  recordedAt: string;
}

interface CajaDto {
  id: string;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  openingBalance: number;
  closingBalance?: number;
  expectedBalance: number;
  difference?: number;
  notes?: string;
  movements: Movement[];
}

type View = 'loading' | 'no_caja' | 'open' | 'closed' | 'open_form';

const CLP = (n: number) =>
  new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(n);

export default function CajaPage() {
  const { token } = useAuthStore();
  const [view, setView] = useState<View>('loading');
  const [caja, setCaja] = useState<CajaDto | null>(null);
  const [openBalance, setOpenBalance] = useState('');
  const [closeBalance, setCloseBalance] = useState('');
  const [movType, setMovType] = useState('IN');
  const [movAmount, setMovAmount] = useState('');
  const [movDesc, setMovDesc] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function loadCurrent() {
    setView('loading');
    const r = await fetch('/api/caja/current', {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.status === 404 || r.status === 400) { setView('no_caja'); return; }
    if (!r.ok) { setView('no_caja'); return; }
    const data: CajaDto = await r.json();
    setCaja(data);
    setView(data.status === 'OPEN' ? 'open' : 'closed');
  }

  useEffect(() => { loadCurrent(); }, [token]);

  async function openCaja() {
    setBusy(true); setError('');
    const r = await fetch('/api/caja/open', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ openingBalance: parseFloat(openBalance) }),
    });
    if (!r.ok) { setError('No se pudo abrir la caja.'); setBusy(false); return; }
    await loadCurrent();
    setBusy(false);
  }

  async function closeCaja() {
    setBusy(true); setError('');
    const r = await fetch('/api/caja/close', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ closingBalance: parseFloat(closeBalance) }),
    });
    if (!r.ok) { setError('No se pudo cerrar la caja.'); setBusy(false); return; }
    await loadCurrent();
    setBusy(false);
  }

  async function addMovement() {
    if (!movAmount || !movDesc.trim()) { setError('Completa todos los campos.'); return; }
    setBusy(true); setError('');
    const r = await fetch('/api/caja/movements', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: movType, amount: parseFloat(movAmount), description: movDesc }),
    });
    if (!r.ok) { setError('No se pudo agregar el movimiento.'); setBusy(false); return; }
    setMovAmount(''); setMovDesc('');
    await loadCurrent();
    setBusy(false);
  }

  if (view === 'loading') return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  if (view === 'no_caja' || view === 'open_form') return (
    <div className="max-w-sm space-y-4">
      <p className="text-pe-charcoal/60 text-sm">No tienes caja abierta.</p>
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
          Balance inicial (CLP)
        </label>
        <input type="number" value={openBalance} onChange={e => setOpenBalance(e.target.value)}
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
      </div>
      {error && <p className="text-red-500 text-sm">{error}</p>}
      <button onClick={openCaja} disabled={busy || !openBalance}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
        {busy ? 'Abriendo...' : 'Abrir caja'}
      </button>
    </div>
  );

  if (!caja) return null;

  if (view === 'closed') return (
    <div className="max-w-lg space-y-4">
      <div className="border border-[#EDE3D8] p-4 space-y-2">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Resumen de cierre</p>
        <div className="grid grid-cols-2 gap-2 text-sm">
          <span className="text-pe-charcoal/60">Balance inicial</span>
          <span className="text-right">{CLP(caja.openingBalance)}</span>
          <span className="text-pe-charcoal/60">Balance esperado</span>
          <span className="text-right">{CLP(caja.expectedBalance)}</span>
          <span className="text-pe-charcoal/60">Balance declarado</span>
          <span className="text-right">{CLP(caja.closingBalance ?? 0)}</span>
          <span className="text-pe-charcoal/60">Diferencia</span>
          <span className={`text-right font-medium ${(caja.difference ?? 0) === 0 ? 'text-green-600' : 'text-red-500'}`}>
            {CLP(caja.difference ?? 0)}
          </span>
        </div>
      </div>
      <button onClick={() => setView('open_form')}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors">
        Abrir nueva caja
      </button>
    </div>
  );

  return (
    <div className="max-w-2xl space-y-6">
      <div className="border border-[#EDE3D8] p-4 flex justify-between items-center">
        <div>
          <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Caja abierta</p>
          <p className="text-2xl font-display text-pe-black mt-1">{CLP(caja.expectedBalance)}</p>
          <p className="text-xs text-pe-charcoal/50 mt-0.5">Balance esperado actual</p>
        </div>
        <span className="px-3 py-1 bg-green-100 text-green-700 text-xs tracking-widest uppercase">Abierta</span>
      </div>

      <div>
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Movimientos</p>
        {caja.movements.length === 0 && (
          <p className="text-pe-charcoal/40 text-sm">Sin movimientos aún.</p>
        )}
        <ul className="divide-y divide-[#EDE3D8]">
          {caja.movements.map(m => (
            <li key={m.id} className="flex items-center justify-between py-2.5 text-sm">
              <div>
                <span className={`text-[10px] tracking-widest uppercase mr-2 ${
                  m.type === 'SALE' || m.type === 'IN' ? 'text-green-600' : 'text-red-500'
                }`}>{m.type}</span>
                <span className="text-pe-charcoal/70">{m.description}</span>
              </div>
              <span className={`font-medium ${m.type === 'SALE' || m.type === 'IN' ? 'text-green-600' : 'text-red-500'}`}>
                {m.type === 'OUT' || m.type === 'REFUND' ? '-' : '+'}{CLP(m.amount)}
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div className="border border-[#EDE3D8] p-4 space-y-3">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Agregar movimiento</p>
        <div className="flex gap-3">
          <select value={movType} onChange={e => setMovType(e.target.value)}
            className="border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]">
            <option value="IN">Entrada</option>
            <option value="OUT">Salida</option>
          </select>
          <input type="number" value={movAmount} onChange={e => setMovAmount(e.target.value)}
            placeholder="Monto" className="flex-1 border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        </div>
        <input type="text" value={movDesc} onChange={e => setMovDesc(e.target.value)}
          placeholder="Descripción" className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        <button onClick={addMovement} disabled={busy}
          className="bg-[#1A1A1A] text-[#F8F4EF] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
          Agregar
        </button>
      </div>

      <div className="border border-[#EDE3D8] p-4 space-y-3">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Cerrar caja</p>
        <p className="text-xs text-pe-charcoal/50">Balance esperado: {CLP(caja.expectedBalance)}</p>
        <input type="number" value={closeBalance} onChange={e => setCloseBalance(e.target.value)}
          placeholder="Balance declarado"
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        <button onClick={closeCaja} disabled={busy || !closeBalance}
          className="border border-[#1A1A1A] text-[#1A1A1A] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-red-50 hover:border-red-400 hover:text-red-600 transition-colors disabled:opacity-50">
          {busy ? 'Cerrando...' : 'Cerrar caja'}
        </button>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}
    </div>
  );
}
