import { useEffect, useMemo, useState } from 'react';
import { Loader2, Search, Send } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  searchProducts,
  publishProductsBatch,
  type ProductDto,
  type PublishProductsBatchItemResult,
} from '../../lib/api';

type Platform = 'INSTAGRAM' | 'FACEBOOK';

const PLATFORM_LABELS: Record<Platform, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
};

function formatClp(amount: number): string {
  return new Intl.NumberFormat('es-CL').format(amount);
}

function interpolateCaption(template: string, product: ProductDto): string {
  return template
    .replaceAll('{producto}', product.name)
    .replaceAll('{precio}', `$${formatClp(product.price.amount)}`);
}

export default function PublicacionesPage() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [term, setTerm] = useState('');
  const [browseRequested, setBrowseRequested] = useState(false);
  const [results, setResults] = useState<ProductDto[]>([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<Map<string, ProductDto>>(new Map());
  const [platforms, setPlatforms] = useState<Set<Platform>>(new Set(['INSTAGRAM', 'FACEBOOK']));
  const [captionTemplate, setCaptionTemplate] = useState(
    '{producto} a solo {precio}. Envios a todo Chile.',
  );
  const [hashtagsInput, setHashtagsInput] = useState('#pilarestilo');
  const [campaignLabel, setCampaignLabel] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [publishResults, setPublishResults] = useState<PublishProductsBatchItemResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const q = term.trim();
    // Below 2 letters normally means "not enough to search yet" — except once the user has
    // clicked into the box at all, an empty box means "browse the catalog", not "no results".
    if (q.length < 2 && !browseRequested) {
      setResults([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const id = setTimeout(() => {
      void searchProducts({ q, page: 0, size: 24 }, 0, 24)
        .then((page) => {
          if (!cancelled) setResults(page.content);
        })
        .catch(() => {
          if (!cancelled) setResults([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(id);
    };
  }, [term, browseRequested]);

  function toggleProduct(product: ProductDto) {
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(product.id)) {
        next.delete(product.id);
      } else {
        next.set(product.id, product);
      }
      return next;
    });
  }

  function togglePlatform(platform: Platform) {
    setPlatforms((prev) => {
      const next = new Set(prev);
      if (next.has(platform)) {
        next.delete(platform);
      } else {
        next.add(platform);
      }
      return next;
    });
  }

  const selectedProducts = useMemo(() => Array.from(selected.values()), [selected]);
  const hashtags = useMemo(
    () =>
      hashtagsInput
        .split(/[\s,]+/)
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
    [hashtagsInput],
  );

  const canPublish =
    selectedProducts.length > 0 && platforms.size > 0 && captionTemplate.trim().length > 0 && !publishing;

  async function handlePublish() {
    if (!canPublish) return;
    setPublishing(true);
    setError(null);
    setPublishResults(null);
    try {
      const response = await publishProductsBatch(
        {
          productIds: selectedProducts.map((p) => p.id),
          platforms: Array.from(platforms),
          captionTemplate,
          hashtags,
          campaignLabel: campaignLabel.trim() || undefined,
        },
        effectiveToken,
      );
      setPublishResults(response.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo publicar el lote.');
    } finally {
      setPublishing(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">1. Elegi los productos</h2>
        <label className="relative block max-w-md">
          <span className="sr-only">Buscar producto</span>
          <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-pe-muted" />
          <input
            type="search"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            onFocus={() => setBrowseRequested(true)}
            placeholder="Buscar producto por nombre, o hace clic para ver el catalogo..."
            className="w-full bg-pe-surface border border-pe-border rounded-xs pl-9 pr-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>

        {selectedProducts.length > 0 && (
          <div className="mt-3">
            <p className="text-xs text-pe-muted mb-1.5">{selectedProducts.length} producto(s) elegido(s)</p>
            <ul className="flex flex-wrap gap-2">
              {selectedProducts.map((product) => (
                <li key={product.id}>
                  <button
                    type="button"
                    onClick={() => toggleProduct(product)}
                    className="group flex items-center gap-1.5 border border-pe-rose bg-pe-rose/10 rounded-xs pl-1 pr-2 py-1"
                  >
                    <img src={product.imageUrl} alt="" className="w-6 h-7 object-cover flex-shrink-0" />
                    <span className="font-sans text-[0.7rem] max-w-32 truncate">{product.name}</span>
                    <span aria-hidden="true" className="text-pe-muted group-hover:text-pe-rose">×</span>
                    <span className="sr-only">Quitar {product.name}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {searching && (
          <div className="flex items-center gap-2 mt-3 text-pe-muted text-xs">
            <Loader2 size={14} className="animate-spin" /> Buscando...
          </div>
        )}

        {results.length > 0 && (
          <ul className="grid grid-cols-2 sm:grid-cols-4 gap-2 mt-3">
            {results.map((product) => {
              const isSelected = selected.has(product.id);
              return (
                <li key={product.id}>
                  <button
                    type="button"
                    onClick={() => toggleProduct(product)}
                    aria-pressed={isSelected}
                    className={[
                      'group relative block w-full overflow-hidden border text-left transition-colors',
                      isSelected ? 'border-pe-rose' : 'border-pe-border hover:border-pe-rose',
                    ].join(' ')}
                  >
                    <img src={product.imageUrl} alt={product.name} loading="lazy" className="aspect-4/5 w-full object-cover" />
                    <span className="block truncate px-1.5 py-1 font-sans text-[0.66rem]">{product.name}</span>
                    {isSelected && (
                      <span className="absolute top-1 right-1 bg-pe-rose text-pe-white text-[0.6rem] px-1.5 py-0.5 rounded-xs">
                        Elegido
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">2. Plataformas</h2>
        <div className="flex gap-4">
          {(['INSTAGRAM', 'FACEBOOK'] as const).map((platform) => (
            <label key={platform} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={platforms.has(platform)}
                onChange={() => togglePlatform(platform)}
                className="accent-pe-rose w-4 h-4"
              />
              {PLATFORM_LABELS[platform]}
            </label>
          ))}
        </div>
      </section>

      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">3. Texto del post</h2>
        <label className="block">
          <span className="text-xs text-pe-muted">
            Plantilla — variables disponibles: <code>{'{producto}'}</code> y <code>{'{precio}'}</code>
          </span>
          <textarea
            value={captionTemplate}
            onChange={(e) => setCaptionTemplate(e.target.value)}
            rows={3}
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
        <label className="block mt-3">
          <span className="text-xs text-pe-muted">Hashtags</span>
          <input
            type="text"
            value={hashtagsInput}
            onChange={(e) => setHashtagsInput(e.target.value)}
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
        <label className="block mt-3 max-w-xs">
          <span className="text-xs text-pe-muted">Campana (opcional)</span>
          <input
            type="text"
            value={campaignLabel}
            onChange={(e) => setCampaignLabel(e.target.value)}
            placeholder="Liquidacion primavera"
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
      </section>

      {selectedProducts.length > 0 && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">4. Vista previa</h2>
          <ul className="flex flex-col gap-3">
            {selectedProducts.map((product) => (
              <li key={product.id} className="flex gap-3 border border-pe-border p-3">
                <img src={product.imageUrl} alt={product.name} className="w-16 h-20 object-cover flex-shrink-0" />
                <p className="text-sm whitespace-pre-wrap">
                  {interpolateCaption(captionTemplate, product)}
                  {hashtags.length > 0 && (
                    <>
                      {'\n\n'}
                      {hashtags.join(' ')}
                    </>
                  )}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}

      {error && (
        <p className="text-sm text-pe-danger-ink" role="alert">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={() => void handlePublish()}
        disabled={!canPublish}
        className="self-start flex items-center gap-2 bg-pe-rose text-pe-white px-4 py-2 rounded-xs text-sm disabled:opacity-50"
      >
        {publishing ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
        Publicar ahora
      </button>

      {publishResults && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">Resultado</h2>
          <ul className="flex flex-col gap-1">
            {publishResults.map((item, index) => {
              const product = selected.get(item.productId);
              return (
                <li key={`${item.productId}-${item.platform}-${index}`} className="text-sm flex items-center gap-2">
                  <span aria-hidden="true">{item.success ? '✓' : '✗'}</span>
                  <span>
                    {product ? product.name : item.productId} — {PLATFORM_LABELS[item.platform]}
                    {!item.success && item.errorMessage ? `: ${item.errorMessage}` : ''}
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
