import { useEffect, useState } from 'react';
import PublicarTab, { type PublicarTabPreload } from './PublicarTab';
import HistorialTab from './HistorialTab';
import CampanasTab from './CampanasTab';

type Tab = 'publicar' | 'historial' | 'campanas';

function parseTab(raw: string | null): Tab {
  const v = raw?.toLowerCase();
  if (v === 'historial') return 'historial';
  if (v === 'campanas') return 'campanas';
  return 'publicar';
}

const TAB_LABEL: Record<Tab, string> = {
  publicar: 'Publicar',
  historial: 'Historial',
  campanas: 'Campañas',
};

export default function PublicacionesPage() {
  const [tab, setTab] = useState<Tab>('publicar');
  const [synced, setSynced] = useState(false);
  const [preload, setPreload] = useState<PublicarTabPreload | undefined>(undefined);
  const [editingBatchId, setEditingBatchId] = useState<string | null>(null);

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
    setEditingBatchId(null);
    setPreload(next);
    setTab('publicar');
  }

  function editScheduled(batchId: string, next: PublicarTabPreload) {
    setEditingBatchId(batchId);
    setPreload(next);
    setTab('publicar');
  }

  function clearEditing() {
    setEditingBatchId(null);
    setPreload(undefined);
  }

  return (
    <div className="flex flex-col gap-5">
      <div role="tablist" aria-label="Publicaciones" className="flex gap-1 border-b border-pe-border">
        {(['publicar', 'historial', 'campanas'] as const).map((id) => (
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
            {TAB_LABEL[id]}
          </button>
        ))}
      </div>

      {tab === 'publicar' && (
        <PublicarTab
          preload={preload}
          onPreloadConsumed={() => setPreload(undefined)}
          editingBatchId={editingBatchId ?? undefined}
          onEditCancelled={clearEditing}
          onPublished={() => setTab('historial')}
        />
      )}
      {tab === 'historial' && (
        <HistorialTab
          onRepublish={republish}
          onGoToPublish={() => setTab('publicar')}
          onEditScheduled={editScheduled}
        />
      )}
      {tab === 'campanas' && <CampanasTab />}
    </div>
  );
}
