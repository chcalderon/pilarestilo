import { useEffect, useMemo, useState, type FocusEvent } from 'react';
import { Download, Loader2, Search, Send, Upload } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  searchProducts,
  publishProductsBatch,
  updateScheduledBatch,
  uploadMediaFile,
  getProduct,
  getProductPublicationImageHistory,
  type ProductDto,
  type ProductVariantDto,
  type PublishProductsBatchItemResult,
} from '../../lib/api';
import { santiagoWallTimeToInstant, instantToSantiagoInputValue, instantToSantiagoLabel } from '../../lib/santiagoTime';

type Platform = 'INSTAGRAM' | 'FACEBOOK';
type VariantSelection = { color: string; size: string };

export type PublicarTabPreload = {
  productIds: string[];
  captionTemplate: string;
  hashtags: string[];
  campaignLabel: string | null;
  scheduledAt?: string | null;
};

type PublicarTabProps = {
  preload?: PublicarTabPreload;
  onPreloadConsumed?: () => void;
  editingBatchId?: string;
  onEditCancelled?: () => void;
};

const PLATFORM_LABELS: Record<Platform, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
};

const VARIANT_TOKENS = ['color', 'talla', 'cantidad'] as const;

function formatClp(amount: number): string {
  return new Intl.NumberFormat('es-CL').format(amount);
}

function findVariant(product: ProductDto, selection?: VariantSelection): ProductVariantDto | undefined {
  if (!selection) return undefined;
  return product.variants?.find((v) => v.color === selection.color && v.size === selection.size);
}

/** Prefers a variant that actually has stock, falling back to the first one so the picker
 *  always starts on something real instead of an empty pair of dropdowns. */
function pickDefaultVariant(variants: ProductVariantDto[]): ProductVariantDto | undefined {
  return variants.find((v) => v.stockAvailable > 0) ?? variants[0];
}

function interpolateCaption(template: string, product: ProductDto, selection?: VariantSelection): string {
  const variant = findVariant(product, selection);
  return template
    .replaceAll('{producto}', product.name)
    .replaceAll('{precio}', `$${formatClp(product.price.amount)}`)
    .replaceAll('{color}', variant?.color ?? '')
    .replaceAll('{talla}', variant?.size ?? '')
    .replaceAll('{cantidad}', variant ? String(variant.stockAvailable) : '');
}

/** Which of {color}/{talla}/{cantidad} the template actually uses but this product can't fill
 *  right now (no variant chosen, or no variants at all) — so the preview can flag the gap
 *  instead of quietly publishing a caption with a blank in it. */
function missingVariantTokens(template: string, product: ProductDto, selection?: VariantSelection): string[] {
  const variant = findVariant(product, selection);
  const values: Record<(typeof VARIANT_TOKENS)[number], string> = {
    color: variant?.color ?? '',
    talla: variant?.size ?? '',
    cantidad: variant ? String(variant.stockAvailable) : '',
  };
  return VARIANT_TOKENS.filter((token) => template.includes(`{${token}}`) && values[token] === '');
}

function joinSpanishList(items: string[]): string {
  if (items.length <= 1) return items.join('');
  return `${items.slice(0, -1).join(', ')} y ${items[items.length - 1]}`;
}

export default function PublicarTab(
  { preload, onPreloadConsumed, editingBatchId, onEditCancelled }: PublicarTabProps = {},
) {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [term, setTerm] = useState('');
  const [browseRequested, setBrowseRequested] = useState(false);
  const [listOpen, setListOpen] = useState(false);
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
  const [scheduledConfirmation, setScheduledConfirmation] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<'now' | 'schedule'>('now');
  const [scheduleInput, setScheduleInput] = useState('');
  const [imageOverrides, setImageOverrides] = useState<Map<string, string>>(new Map());
  const [uploadingFor, setUploadingFor] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [imageHistory, setImageHistory] = useState<Map<string, string[]>>(new Map());
  const [variantSelections, setVariantSelections] = useState<Map<string, VariantSelection>>(new Map());

  useEffect(() => {
    if (!preload) return;
    let cancelled = false;
    setCaptionTemplate(preload.captionTemplate);
    setHashtagsInput(preload.hashtags.join(' '));
    setCampaignLabel(preload.campaignLabel ?? '');
    if (preload.scheduledAt) {
      setMode('schedule');
      setScheduleInput(instantToSantiagoInputValue(preload.scheduledAt));
    }
    void Promise.all(preload.productIds.map((id) => getProduct(id).catch(() => null))).then((loaded) => {
      if (cancelled) return;
      setSelected(() => {
        const next = new Map<string, ProductDto>();
        loaded.forEach((prod) => {
          if (prod) next.set(prod.id, prod);
        });
        return next;
      });
      onPreloadConsumed?.();
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [preload]);

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
    const wasSelected = selected.has(product.id);
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(product.id)) {
        next.delete(product.id);
      } else {
        next.set(product.id, product);
      }
      return next;
    });
    if (!wasSelected && !imageHistory.has(product.id)) {
      void getProductPublicationImageHistory(product.id, effectiveToken)
        .then((urls) => setImageHistory((prev) => new Map(prev).set(product.id, urls)))
        .catch(() => setImageHistory((prev) => new Map(prev).set(product.id, [])));
    }
    if (!wasSelected && product.variants && product.variants.length > 0 && !variantSelections.has(product.id)) {
      const def = pickDefaultVariant(product.variants);
      if (def) {
        setVariantSelections((prev) => new Map(prev).set(product.id, { color: def.color, size: def.size }));
      }
    }
  }

  function setProductColor(product: ProductDto, color: string) {
    const sizesForColor = (product.variants ?? []).filter((v) => v.color === color);
    const bestSize = pickDefaultVariant(sizesForColor)?.size ?? sizesForColor[0]?.size ?? '';
    setVariantSelections((prev) => new Map(prev).set(product.id, { color, size: bestSize }));
  }

  function setProductSize(product: ProductDto, size: string) {
    const current = variantSelections.get(product.id);
    setVariantSelections((prev) => new Map(prev).set(product.id, { color: current?.color ?? '', size }));
  }

  function handleSearchAreaBlur(e: FocusEvent<HTMLElement>) {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
      setListOpen(false);
    }
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

  function effectiveImageUrl(product: ProductDto): string {
    return imageOverrides.get(product.id) ?? product.imageUrl;
  }

  async function handleImageReplace(product: ProductDto, file: File) {
    setUploadingFor(product.id);
    setUploadError(null);
    try {
      const url = await uploadMediaFile(file, 'publications', effectiveToken);
      setImageOverrides((prev) => {
        const next = new Map(prev);
        next.set(product.id, url);
        return next;
      });
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'No se pudo subir la foto editada.');
    } finally {
      setUploadingFor(null);
    }
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
    selectedProducts.length > 0 &&
    platforms.size > 0 &&
    captionTemplate.trim().length > 0 &&
    (mode === 'now' || scheduleInput.length > 0) &&
    !publishing;

  const ctaLabel = editingBatchId
    ? 'Guardar cambios'
    : mode === 'schedule'
      ? 'Programar publicación'
      : 'Publicar ahora';

  async function handleSubmit() {
    if (!canPublish) return;
    setPublishing(true);
    setError(null);
    setPublishResults(null);
    setScheduledConfirmation(null);
    try {
      const relevantVariants = new Map(
        selectedProducts
          .filter((p) => variantSelections.has(p.id))
          .map((p) => [p.id, variantSelections.get(p.id)!] as const),
      );
      const scheduledAt = mode === 'schedule' ? santiagoWallTimeToInstant(scheduleInput) : undefined;
      const payload = {
        productIds: selectedProducts.map((p) => p.id),
        platforms: Array.from(platforms),
        captionTemplate,
        hashtags,
        campaignLabel: campaignLabel.trim() || undefined,
        imageOverrides: imageOverrides.size > 0 ? Object.fromEntries(imageOverrides) : undefined,
        variantSelections: relevantVariants.size > 0 ? Object.fromEntries(relevantVariants) : undefined,
        scheduledAt,
      };
      if (editingBatchId) {
        await updateScheduledBatch(editingBatchId, payload, effectiveToken);
        setScheduledConfirmation('Cambios guardados.');
        onEditCancelled?.();
        return;
      }
      const response = await publishProductsBatch(payload, effectiveToken);
      if (scheduledAt) {
        setScheduledConfirmation(
          `Programada para ${instantToSantiagoLabel(scheduledAt)}. La vas a ver en Historial.`,
        );
      } else {
        setPublishResults(response.items);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo publicar el lote.');
    } finally {
      setPublishing(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section onBlur={handleSearchAreaBlur}>
        <h2 className="font-sans text-sm text-pe-muted mb-2">1. Elige los productos</h2>
        <label className="relative block max-w-md">
          <span className="sr-only">Buscar producto</span>
          <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-pe-muted" />
          <input
            type="search"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            onFocus={() => {
              setBrowseRequested(true);
              setListOpen(true);
            }}
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
                    <img src={effectiveImageUrl(product)} alt="" className="w-6 h-7 object-cover flex-shrink-0" />
                    <span className="font-sans text-[0.7rem] max-w-32 truncate">{product.name}</span>
                    <span aria-hidden="true" className="text-pe-muted group-hover:text-pe-rose">×</span>
                    <span className="sr-only">Quitar {product.name}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {listOpen && searching && (
          <div className="flex items-center gap-2 mt-3 text-pe-muted text-xs">
            <Loader2 size={14} className="animate-spin" /> Buscando...
          </div>
        )}

        {listOpen && results.length > 0 && (
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
            Plantilla — variables disponibles: <code>{'{producto}'}</code>, <code>{'{precio}'}</code>,{' '}
            <code>{'{color}'}</code>, <code>{'{talla}'}</code> y <code>{'{cantidad}'}</code>
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

      <fieldset className="flex flex-col gap-2">
        <legend className="font-sans text-sm text-pe-muted mb-1">Cuando</legend>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="when"
            checked={mode === 'now'}
            onChange={() => setMode('now')}
            className="accent-pe-rose"
          />
          Publicar ahora
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="when"
            checked={mode === 'schedule'}
            onChange={() => setMode('schedule')}
            className="accent-pe-rose"
          />
          Programar
        </label>
        {mode === 'schedule' && (
          <label className="flex flex-col gap-1 text-xs text-pe-muted mt-1 max-w-xs">
            Fecha y hora (hora de Chile)
            <input
              type="datetime-local"
              value={scheduleInput}
              min={instantToSantiagoInputValue(new Date(Date.now() + 5 * 60000).toISOString())}
              onChange={(e) => setScheduleInput(e.target.value)}
              className="bg-pe-surface border border-pe-border rounded-xs px-2 py-1 text-sm text-pe-black"
            />
          </label>
        )}
      </fieldset>

      {selectedProducts.length > 0 && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">4. Vista previa</h2>
          {uploadError && (
            <p className="text-sm text-pe-danger-ink mb-2" role="alert">
              {uploadError}
            </p>
          )}
          <ul className="flex flex-col gap-3">
            {selectedProducts.map((product) => {
              const isUploading = uploadingFor === product.id;
              const hasOverride = imageOverrides.has(product.id);
              const history = (imageHistory.get(product.id) ?? []).filter(
                (url) => url !== effectiveImageUrl(product),
              );
              const variants = product.variants ?? [];
              const selection = variantSelections.get(product.id);
              const colors = Array.from(new Set(variants.map((v) => v.color)));
              const sizesForColor = variants.filter((v) => v.color === selection?.color);
              const currentVariant = findVariant(product, selection);
              const missingTokens = missingVariantTokens(captionTemplate, product, selection);
              return (
                <li key={product.id} className="flex gap-3 border border-pe-border p-3">
                  <img
                    src={effectiveImageUrl(product)}
                    alt={product.name}
                    className="w-16 h-20 object-cover flex-shrink-0"
                  />
                  <div className="flex-1 flex flex-col gap-2">
                    {variants.length > 0 && (
                      <div className="flex items-end gap-3 flex-wrap">
                        <label className="flex flex-col gap-1 text-[0.72rem]">
                          <span className="text-pe-muted">Color</span>
                          <select
                            value={selection?.color ?? ''}
                            onChange={(e) => setProductColor(product, e.target.value)}
                            className="bg-pe-surface border border-pe-border rounded-xs px-2 py-1 text-xs"
                          >
                            {colors.map((color) => (
                              <option key={color} value={color}>
                                {color}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="flex flex-col gap-1 text-[0.72rem]">
                          <span className="text-pe-muted">Talla</span>
                          <select
                            value={selection?.size ?? ''}
                            onChange={(e) => setProductSize(product, e.target.value)}
                            className="bg-pe-surface border border-pe-border rounded-xs px-2 py-1 text-xs"
                          >
                            {sizesForColor.map((v) => (
                              <option key={v.size} value={v.size}>
                                {v.size}
                              </option>
                            ))}
                          </select>
                        </label>
                        {currentVariant && (
                          <span className="text-[0.7rem] text-pe-muted">
                            Stock disponible: {currentVariant.stockAvailable}
                          </span>
                        )}
                      </div>
                    )}
                    <p className="text-sm whitespace-pre-wrap">
                      {interpolateCaption(captionTemplate, product, selection)}
                      {hashtags.length > 0 && (
                        <>
                          {'\n\n'}
                          {hashtags.join(' ')}
                        </>
                      )}
                    </p>
                    {missingTokens.length > 0 && (
                      <p role="status" className="text-[0.72rem] text-pe-warning-ink">
                        Sin variante: {joinSpanishList(missingTokens)} quedará vacío en el texto de este producto.
                      </p>
                    )}
                    <div className="flex items-center gap-3">
                      <a
                        href={effectiveImageUrl(product)}
                        download
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-[0.72rem] text-pe-muted hover:text-pe-rose"
                      >
                        <Download size={12} /> Descargar foto
                      </a>
                      <label className="inline-flex items-center gap-1 text-[0.72rem] text-pe-muted hover:text-pe-rose cursor-pointer">
                        {isUploading ? <Loader2 size={12} className="animate-spin" /> : <Upload size={12} />}
                        {hasOverride ? 'Reemplazar foto editada' : 'Subir foto editada'}
                        <input
                          type="file"
                          accept="image/*"
                          className="sr-only"
                          disabled={isUploading}
                          onChange={(e) => {
                            const file = e.target.files?.[0];
                            e.target.value = '';
                            if (file) void handleImageReplace(product, file);
                          }}
                        />
                      </label>
                    </div>
                    {history.length > 0 && (
                      <div>
                        <p className="text-[0.66rem] text-pe-muted mb-1">Fotos usadas antes en este producto:</p>
                        <ul className="flex gap-1.5">
                          {history.map((url) => (
                            <li key={url}>
                              <button
                                type="button"
                                title="Reusar esta foto"
                                onClick={() =>
                                  setImageOverrides((prev) => new Map(prev).set(product.id, url))
                                }
                                className="block border border-pe-border hover:border-pe-rose"
                              >
                                <img src={url} alt="" className="w-8 h-10 object-cover" />
                              </button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {error && (
        <p className="text-sm text-pe-danger-ink" role="alert">
          {error}
        </p>
      )}

      {scheduledConfirmation && (
        <p className="text-sm text-pe-positive-ink" role="status">
          {scheduledConfirmation}
        </p>
      )}

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => void handleSubmit()}
          disabled={!canPublish}
          className="self-start flex items-center gap-2 bg-pe-rose text-pe-white px-4 py-2 rounded-xs text-sm disabled:opacity-50"
        >
          {publishing ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
          {ctaLabel}
        </button>
        {editingBatchId && (
          <button
            type="button"
            onClick={() => onEditCancelled?.()}
            className="text-sm text-pe-muted hover:text-pe-black"
          >
            Cancelar edición
          </button>
        )}
      </div>

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
