import { useEffect, useState } from 'react';
import PublicarTab, { type PublicarTabPreload } from './PublicarTab';
import HistorialTab from './HistorialTab';

type Tab = 'publicar' | 'historial';

function parseTab(raw: string | null): Tab {
  return raw?.toLowerCase() === 'historial' ? 'historial' : 'publicar';
}

export default function PublicacionesPage() {
  const [tab, setTab] = useState<Tab>('publicar');
  const [synced, setSynced] = useState(false);
  const [preload, setPreload] = useState<PublicarTabPreload | undefined>(undefined);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    setTab(parseTab(new URLSearchParams(window.location.search).get('tab')));
    setSynced(true);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined' || !synced) return;
    const url = new URL(window.location.href);
    if (parseTab(url.searchParams.get('tab')) === tab) return;
    url.searchParams.set('tab', tab);
    window.history.replaceState(window.history.state, '', `${url.pathname}?${url.searchParams.toString()}`);
  }, [tab, synced]);

  function republish(next: PublicarTabPreload) {
    setPreload(next);
    setTab('publicar');
  }

  return (
    <div className="flex flex-col gap-5">
      <div role="tablist" aria-label="Publicaciones" className="flex gap-1 border-b border-pe-border">
        {(['publicar', 'historial'] as const).map((id) => (
          <button
            key={id}
            role="tab"
            type="button"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={[
              'px-3 py-2 text-sm -mb-px border-b-2 transition-colors',
              tab === id ? 'border-pe-rose text-pe-black' : 'border-transparent text-pe-muted hover:text-pe-black',
            ].join(' ')}
          >
            {id === 'publicar' ? 'Publicar' : 'Historial'}
          </button>
        ))}
      </div>

      {tab === 'publicar' && (
        <PublicarTab preload={preload} onPreloadConsumed={() => setPreload(undefined)} />
      )}
      {tab === 'historial' && <HistorialTab onRepublish={republish} onGoToPublish={() => setTab('publicar')} />}
    </div>
  );
}
