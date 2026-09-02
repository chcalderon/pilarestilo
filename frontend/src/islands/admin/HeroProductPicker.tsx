import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, Search, X } from 'lucide-react';
import Overlay from './Overlay';
import {
  assignHeroModelFromProduct,
  searchProducts,
  type HeroModelSlot,
  type ProductDto,
} from '../../lib/api';

interface Props {
  readonly slot: HeroModelSlot;
  readonly token: string;
  readonly onAssigned: () => void;
  readonly onClose: () => void;
}

/**
 * Pick an existing product by looking at its photo, and use that photo as one side of the home
 * hero. The "assign from product" endpoint already exists; this replaces the two "Usar como Hero"
 * buttons that used to live on every product form — the choice belongs here, next to the hero.
 */
export default function HeroProductPicker({ slot, token, onAssigned, onClose }: Props) {
  const slotLabel = slot === 'left' ? 'izquierda' : 'derecha';

  const [term, setTerm] = useState('');
  const [results, setResults] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [assigningId, setAssigningId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const q = term.trim();
    if (q.length < 2) {
      setResults([]);
      setSearched(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    const id = setTimeout(() => {
      void searchProducts({ q, page: 0, size: 24 }, 0, 24)
        .then((page) => {
          if (cancelled) return;
          setResults(page.content.filter((p) => p.imageUrl && p.imageUrl.trim().length > 0));
          setSearched(true);
        })
        .catch(() => {
          if (!cancelled) {
            setResults([]);
            setSearched(true);
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(id);
    };
  }, [term]);

  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const setDialogRef = useCallback((node: HTMLDialogElement | null) => {
    dialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);

  async function assign(product: ProductDto) {
    if (assigningId) return;
    setAssigningId(product.id);
    setError(null);
    try {
      await assignHeroModelFromProduct(slot, product.id, token);
      onAssigned();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo asignar la imagen al hero.');
      setAssigningId(null);
    }
  }

  return (
    <Overlay>
      {/* A <dialog> is interactive; jsx-a11y misreads it. Escape is handled by onCancel; the
          backdrop click is a mouse convenience with that keyboard equivalent. */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/click-events-have-key-events */}
      <dialog
        ref={setDialogRef}
        aria-label={`Elegir imagen para el hero ${slotLabel}`}
        onCancel={(event) => {
          event.preventDefault();
          onClose();
        }}
        onClick={(event) => {
          if (event.target === dialogRef.current) onClose();
        }}
        className="m-auto w-full max-w-[560px] p-0 border-0 bg-[var(--pe-surface-card)] text-[var(--pe-ink)] shadow-2xl backdrop:bg-black/40 backdrop:backdrop-blur-[2px]"
      >
        <div className="flex items-center justify-between border-b border-[var(--pe-border)] px-4 py-3">
          <h2 className="font-sans text-sm">
            Elegir imagen para el hero — {slotLabel}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1 text-[var(--pe-muted)] hover:text-[var(--pe-ink)] transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <div className="p-4">
          <label className="relative block">
            <span className="sr-only">Buscar producto</span>
            <Search
              size={14}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[var(--pe-muted)]"
            />
            <input
              type="search"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Buscar producto por nombre..."
              className="w-full bg-[var(--pe-surface)] border border-[var(--pe-border)] rounded-xs pl-9 pr-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-[var(--pe-border)]"
            />
          </label>

          {error && (
            <p className="mt-3 text-[0.78rem] text-pe-danger-ink" role="alert">
              {error}
            </p>
          )}

          <div className="mt-3 max-h-[60vh] overflow-y-auto">
            {loading && (
              <div className="flex items-center justify-center py-10 text-[var(--pe-muted)]">
                <Loader2 size={18} className="animate-spin" />
              </div>
            )}

            {!loading && !searched && (
              <p className="py-10 text-center text-[0.8rem] text-[var(--pe-muted)]">
                Escribe al menos 2 letras para buscar.
              </p>
            )}

            {!loading && searched && results.length === 0 && (
              <p className="py-10 text-center text-[0.8rem] text-[var(--pe-muted)]">
                Sin productos con imagen para esa busqueda.
              </p>
            )}

            {!loading && results.length > 0 && (
              <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {results.map((product) => {
                  const busy = assigningId === product.id;
                  return (
                    <li key={product.id}>
                      <button
                        type="button"
                        onClick={() => void assign(product)}
                        disabled={assigningId !== null}
                        className="group relative block w-full overflow-hidden border border-[var(--pe-border)] text-left transition-colors hover:border-pe-rose disabled:opacity-60"
                      >
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          loading="lazy"
                          className="aspect-[4/5] w-full object-cover"
                        />
                        <span className="block truncate px-1.5 py-1 font-sans text-[0.66rem] text-[var(--pe-ink)]">
                          {product.name}
                        </span>
                        {busy && (
                          <span className="absolute inset-0 flex items-center justify-center bg-black/40">
                            <Loader2 size={18} className="animate-spin text-white" />
                          </span>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      </dialog>
    </Overlay>
  );
}
