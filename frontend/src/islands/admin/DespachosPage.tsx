import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

interface DispatchDto {
  id: string;
  orderId: string;
  dispatcherId: string | null;
  status: string;
  carrier: string | null;
  trackingCode: string | null;
  notes: string | null;
  createdAt: string;
}

export default function DespachosPage() {
  const { token } = useAuthStore();
  const [dispatches, setDispatches] = useState<DispatchDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<DispatchDto | null>(null);
  const [carrier, setCarrier] = useState('');
  const [tracking, setTracking] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    const r = await fetch('/api/despachos', { headers: { Authorization: `Bearer ${token}` } });
    if (!r.ok) { setLoading(false); return; }
    const data: DispatchDto[] = await r.json();
    setDispatches(data);
    setLoading(false);
  }

  useEffect(() => { load(); }, [token]);

  async function claim(id: string) {
    setBusy(true);
    await fetch(`/api/despachos/${id}/claim`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }
    });
    await load(); setBusy(false);
  }

  async function unclaim(id: string) {
    setBusy(true);
    await fetch(`/api/despachos/${id}/unclaim`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }
    });
    await load(); setBusy(false);
  }

  async function dispatchOrder(id: string) {
    if (!carrier || !tracking) { setError('Ingresa carrier y código de seguimiento.'); return; }
    setBusy(true); setError('');
    await fetch(`/api/despachos/${id}/dispatch`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ carrier, trackingCode: tracking }),
    });
    setActive(null); setCarrier(''); setTracking('');
    await load(); setBusy(false);
  }

  if (loading) return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  const pending = dispatches.filter(d => d.status === 'PENDING');
  const inProgress = dispatches.filter(d => d.status === 'IN_PROGRESS');
  const done = dispatches.filter(d => ['DISPATCHED', 'DELIVERED', 'FAILED'].includes(d.status));

  return (
    <div className="space-y-8">
      {inProgress.length > 0 && (
        <section>
          <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">En progreso</h2>
          <ul className="space-y-3">
            {inProgress.map(d => (
              <li key={d.id} className="border border-[#EDE3D8] p-4">
                <p className="text-sm text-pe-charcoal/70 mb-2">Orden {d.orderId.substring(0, 8)}</p>
                {active?.id === d.id ? (
                  <div className="space-y-2">
                    <input type="text" value={carrier} onChange={e => setCarrier(e.target.value)}
                      placeholder="Carrier (ej. Chilexpress)"
                      className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
                    <input type="text" value={tracking} onChange={e => setTracking(e.target.value)}
                      placeholder="Código de seguimiento"
                      className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
                    {error && <p className="text-red-500 text-xs">{error}</p>}
                    <div className="flex gap-2">
                      <button onClick={() => dispatchOrder(d.id)} disabled={busy}
                        className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
                        Marcar despachado
                      </button>
                      <button onClick={() => setActive(null)}
                        className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50">
                        Cancelar
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex gap-2">
                    <button onClick={() => setActive(d)}
                      className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors">
                      Despachar
                    </button>
                    <button onClick={() => unclaim(d.id)} disabled={busy}
                      className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50">
                      Liberar
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">
          Pendientes ({pending.length})
        </h2>
        {pending.length === 0 && <p className="text-pe-charcoal/40 text-sm">Sin órdenes pendientes.</p>}
        <ul className="space-y-2">
          {pending.map(d => (
            <li key={d.id} className="border border-[#EDE3D8] p-4 flex items-center justify-between">
              <p className="text-sm text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</p>
              <button onClick={() => claim(d.id)} disabled={busy}
                className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
                Tomar
              </button>
            </li>
          ))}
        </ul>
      </section>

      {done.length > 0 && (
        <section>
          <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Completados</h2>
          <ul className="space-y-2">
            {done.map(d => (
              <li key={d.id} className="border border-[#EDE3D8] p-3 flex items-center justify-between text-sm">
                <span className="text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</span>
                <span className={`text-xs tracking-widest uppercase ${
                  d.status === 'DELIVERED' ? 'text-green-600' :
                  d.status === 'FAILED' ? 'text-red-500' : 'text-blue-500'
                }`}>{d.status}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
