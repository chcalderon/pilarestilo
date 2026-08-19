import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Plus,
  RefreshCw,
  LayoutGrid,
  Rows3,
  PencilLine,
  Trash2,
  PackageSearch,
  ChevronLeft,
  ChevronRight,
  Search,
  X,
} from 'lucide-react';
import {
  assignHeroModelFromProduct,
  searchProducts,
  deleteProduct,
  getCategories,
  type ProductDto,
  type CategoryDto,
} from '../../lib/api';
import { summarizeProductVariantSecondaryValues } from '../../lib/productVariants';
import { getProductVariantSchema, getSecondaryAttribute } from '../../lib/variantSchema';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column, type BulkAction } from './DataTable';
import ProductForm from './ProductForm';

type ViewMode = 'grid' | 'cards';

const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
const VIEW_MODE_KEY = 'pe-admin-products-view';
const PAGE_SIZE_KEY = 'pe-admin-products-page-size';

interface ParsedQuery {
  q: string;
  active?: boolean;
  condition?: 'NEW' | 'USED';
  category?: string;
  matchedTokens: string[];
}

function normalizeToken(token: string): string {
  return token
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

const ACTIVE_TRUE = new Set(['activo', 'activa', 'activos', 'activas', 'enabled', 'visible']);
const ACTIVE_FALSE = new Set(['inactivo', 'inactiva', 'inactivos', 'inactivas', 'disabled', 'oculto', 'oculta']);
const CONDITION_NEW = new Set(['nuevo', 'nueva', 'nuevos', 'nuevas', 'new']);
const CONDITION_USED = new Set(['usado', 'usada', 'usados', 'usadas', 'used', 'segundamano']);

/**
 * Smart parser: extracts magic-word filters from the search input and returns
 * remaining free text as `q`. Magic words consumed (not echoed in q):
 *   - activo / inactivo → active=true|false
 *   - nuevo / usado     → condition=NEW|USED
 *   - <category nameEs> → category=<slug> (exact normalized match against catalog)
 */
function parseSearchInput(input: string, categories: CategoryDto[]): ParsedQuery {
  const trimmed = input.trim();
  if (!trimmed) return { q: '', matchedTokens: [] };

  const catBySlug = new Map<string, string>();
  const catByName = new Map<string, string>();
  for (const c of categories) {
    catBySlug.set(normalizeToken(c.slug), c.slug);
    catByName.set(normalizeToken(c.nameEs), c.slug);
    catByName.set(normalizeToken(c.nameEn), c.slug);
  }

  const tokens = trimmed.split(/\s+/);
  const remaining: string[] = [];
  const matchedTokens: string[] = [];
  let active: boolean | undefined;
  let condition: 'NEW' | 'USED' | undefined;
  let category: string | undefined;

  for (const raw of tokens) {
    const norm = normalizeToken(raw);
    if (ACTIVE_TRUE.has(norm)) {
      active = true;
      matchedTokens.push(raw);
    } else if (ACTIVE_FALSE.has(norm)) {
      active = false;
      matchedTokens.push(raw);
    } else if (CONDITION_NEW.has(norm)) {
      condition = 'NEW';
      matchedTokens.push(raw);
    } else if (CONDITION_USED.has(norm)) {
      condition = 'USED';
      matchedTokens.push(raw);
    } else if (!category && (catBySlug.has(norm) || catByName.has(norm))) {
      category = catBySlug.get(norm) ?? catByName.get(norm);
      matchedTokens.push(raw);
    } else {
      remaining.push(raw);
    }
  }

  return {
    q: remaining.join(' '),
    active,
    condition,
    category,
    matchedTokens,
  };
}

export default function ProductTable() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>('');
  const [sortKey, setSortKey] = useState<string | undefined>(undefined);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
  const [createdFrom, setCreatedFrom] = useState('');
  const [createdTo, setCreatedTo] = useState('');
  const [editTarget, setEditTarget] = useState<ProductDto | null | undefined>(undefined);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [heroAssigningKey, setHeroAssigningKey] = useState<string | null>(null);
  const [heroAssignmentFeedback, setHeroAssignmentFeedback] = useState<string>('');
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [viewMode, setViewMode] = useState<ViewMode>('grid');
  const [isMobileViewport, setIsMobileViewport] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const saved = window.localStorage.getItem(VIEW_MODE_KEY);
    if (saved === 'grid' || saved === 'cards') {
      setViewMode(saved);
      return;
    }
    if (window.innerWidth < 1024) {
      setViewMode('cards');
    }

    const savedSizeRaw = window.localStorage.getItem(PAGE_SIZE_KEY);
    const savedSize = savedSizeRaw ? Number(savedSizeRaw) : NaN;
    if (PAGE_SIZE_OPTIONS.includes(savedSize as (typeof PAGE_SIZE_OPTIONS)[number])) {
      setPageSize(savedSize);
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onResize = () => setIsMobileViewport(window.innerWidth < 768);
    onResize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(VIEW_MODE_KEY, viewMode);
  }, [viewMode]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(PAGE_SIZE_KEY, String(pageSize));
  }, [pageSize]);

  useEffect(() => {
    let mounted = true;
    getCategories()
      .then((res) => {
        if (!mounted) return;
        setCategories(res);
      })
      .catch(() => {
        if (!mounted) return;
        setCategories([]);
      });
    return () => {
      mounted = false;
    };
  }, []);

  // Debounce search input → query (250ms)
  useEffect(() => {
    const handle = window.setTimeout(() => setDebouncedSearch(searchInput), 250);
    return () => window.clearTimeout(handle);
  }, [searchInput]);

  // Reset page when search changes
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  useEffect(() => {
    setPage(0);
  }, [createdFrom, createdTo]);

  const parsed = useMemo(
    () => parseSearchInput(debouncedSearch, categories),
    [debouncedSearch, categories],
  );

  const sortParam = useMemo(() => {
    if (!sortKey) return undefined;
    const sortFieldByKey: Record<string, string> = {
      name: 'name',
      brand: 'brand',
      price: 'priceAmount',
      createdAt: 'createdAt',
    };
    const sortField = sortFieldByKey[sortKey];
    if (!sortField) return undefined;
    return `${sortField},${sortDir}`;
  }, [sortKey, sortDir]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const res = await searchProducts({
        q: parsed.q,
        active: parsed.active,
        condition: parsed.condition,
        category: parsed.category,
        createdFrom: createdFrom || undefined,
        createdTo: createdTo || undefined,
        sort: sortParam,
        page,
        size: pageSize,
      });
      setProducts(res.content);
      setTotal(res.totalElements);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No se pudo cargar el listado de productos.';
      setLoadError(message);
      setProducts([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, parsed.q, parsed.active, parsed.condition, parsed.category, createdFrom, createdTo, sortParam]);

  useEffect(() => {
    load();
  }, [load]);

  function handleSort(key: string) {
    setPage(0);
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  async function handleDelete(id: string) {
    if (!effectiveToken) {
      alert('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    setDeleting(true);
    try {
      await deleteProduct(id, effectiveToken);
      setProducts((prev) => prev.filter((p) => p.id !== id));
      setTotal((prev) => prev - 1);
    } catch {
      alert('Error al eliminar el producto.');
    } finally {
      setDeleting(false);
      setDeleteConfirm(null);
    }
  }

  async function handleAssignHeroModel(slot: 'left' | 'right', product: ProductDto) {
    if (!effectiveToken) {
      alert('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    const key = `${product.id}:${slot}`;
    setHeroAssigningKey(key);
    setHeroAssignmentFeedback('');
    try {
      await assignHeroModelFromProduct(slot, product.id, effectiveToken);
      setHeroAssignmentFeedback(
        `Hero ${slot === 'left' ? 'izquierdo' : 'derecho'} actualizado desde "${product.name}".`,
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No se pudo asignar la imagen al hero.';
      alert(message);
    } finally {
      setHeroAssigningKey(null);
    }
  }

  function handleSaved(saved: ProductDto) {
    setProducts((prev) => {
      const idx = prev.findIndex((p) => p.id === saved.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = saved;
        return next;
      }
      return [saved, ...prev];
    });
    if (editTarget === null) setTotal((t) => t + 1);
    setEditTarget(undefined);
  }

  const fmt = (amount: number, currency: string) =>
    new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  const getDiscountPercent = (row: ProductDto) => {
    if (!row.listPrice || row.listPrice.currency !== row.price.currency) return null;
    if (row.listPrice.amount <= row.price.amount) return null;
    return Math.round((1 - row.price.amount / row.listPrice.amount) * 100);
  };
  const fmtCreatedAt = (value: string) =>
    new Date(value).toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const formatSizesSummary = (row: ProductDto) => summarizeProductVariantSecondaryValues(row);
  const formatSecondaryAttributeLabel = (row: ProductDto) => getSecondaryAttribute(getProductVariantSchema(row)).label;

  const actionButtonClass =
    'inline-flex items-center gap-1.5 font-sans text-[0.68rem] uppercase tracking-[0.1em] transition-colors';

  const columns: Column<ProductDto>[] = [
    {
      key: 'image',
      header: '',
      width: '56px',
      render: (row) => (
        <img
          src={row.imageUrl}
          alt={row.name}
          width={36}
          height={44}
          className="w-9 h-11 object-cover bg-pe-cream"
          loading="lazy"
        />
      ),
    },
    {
      key: 'name',
      header: 'Nombre',
      sortable: true,
      render: (row) => (
        <span className="block font-sans text-[0.82rem] font-medium text-pe-charcoal leading-snug max-w-[180px] truncate">
          {row.name}
        </span>
      ),
    },
    {
      key: 'brand',
      header: 'Marca',
      sortable: true,
      render: (row) => (
        <span className="font-sans text-[0.72rem] tracking-[0.1em] uppercase text-pe-rose-deep/80">{row.brand}</span>
      ),
    },
    {
      key: 'condition',
      header: 'Condicion',
      render: (row) => (
        <span
          className={[
            'font-sans text-[0.65rem] tracking-[0.12em] uppercase px-2 py-0.5',
            row.condition === 'NEW'
              ? 'bg-pe-black text-pe-offwhite'
              : 'bg-pe-rose-soft text-black border border-pe-rose/45',
          ].join(' ')}
        >
          {row.condition === 'NEW' ? 'Nuevo' : 'Usado'}
        </span>
      ),
    },
    {
      key: 'price',
      header: 'Precio',
      sortable: true,
      render: (row) => {
        const discountPct = getDiscountPercent(row);
        return (
          <div className="flex flex-col leading-tight">
            {discountPct !== null && (
              <span className="font-sans text-[0.68rem] text-pe-charcoal/45 line-through">
                {fmt(row.listPrice!.amount, row.listPrice!.currency)}
              </span>
            )}
            <span className="font-sans text-[0.82rem] text-pe-charcoal">
              {fmt(row.price.amount, row.price.currency)}
              {discountPct !== null && <span className="ml-1 text-[0.66rem] text-emerald-700">-{discountPct}%</span>}
            </span>
          </div>
        );
      },
    },
    {
      key: 'stock',
      header: 'Stock',
      width: '64px',
      render: (row) => {
        const combinationCount = row.variants?.length ?? 0;
        const sizesSummary = formatSizesSummary(row);
        return (
          <div className="flex flex-col leading-tight">
            <span
              className={[
                'font-sans text-[0.82rem]',
                row.stock === 0 ? 'text-red-500' : row.stock <= 2 ? 'text-amber-600' : 'text-pe-charcoal',
              ].join(' ')}
            >
              {row.stock}
            </span>
            {combinationCount > 0 && (
              <span className="font-sans text-[0.62rem] uppercase tracking-[0.1em] text-pe-charcoal/45">
                {combinationCount} comb.
              </span>
            )}
            {sizesSummary && (
              <span className="font-sans text-[0.6rem] uppercase tracking-[0.08em] text-pe-charcoal/40">
                {formatSecondaryAttributeLabel(row)}: {sizesSummary}
              </span>
            )}
          </div>
        );
      },
    },
    {
      key: 'active',
      header: 'Activo',
      width: '84px',
      render: (row) => (
        <span
          className={[
            'inline-flex items-center rounded-sm px-2 py-0.5 font-sans text-[0.65rem] uppercase tracking-[0.1em]',
            row.active ? 'bg-emerald-50 text-emerald-700' : 'bg-pe-cream text-pe-charcoal/45',
          ].join(' ')}
        >
          {row.active ? 'Si' : 'No'}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Ingreso',
      sortable: true,
      width: '110px',
      render: (row) => (
        <span className="font-sans text-[0.74rem] text-pe-charcoal/65">
          {fmtCreatedAt(row.createdAt)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '320px',
      render: (row) => (
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setEditTarget(row);
            }}
            className={`${actionButtonClass} text-pe-charcoal/55 hover:text-pe-rose-deep`}
          >
            <PencilLine size={13} />
            Editar
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setDeleteConfirm(row.id);
            }}
            className={`${actionButtonClass} text-pe-charcoal/55 hover:text-red-500`}
          >
            <Trash2 size={13} />
            Eliminar
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              void handleAssignHeroModel('left', row);
            }}
            disabled={heroAssigningKey !== null}
            className="inline-flex items-center gap-1 border border-pe-black/15 px-2 py-1 font-sans text-[0.58rem] uppercase tracking-[0.1em] text-pe-charcoal/70 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
          >
            {heroAssigningKey === `${row.id}:left` ? '...' : 'Hero Izq'}
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              void handleAssignHeroModel('right', row);
            }}
            disabled={heroAssigningKey !== null}
            className="inline-flex items-center gap-1 border border-pe-black/15 px-2 py-1 font-sans text-[0.58rem] uppercase tracking-[0.1em] text-pe-charcoal/70 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
          >
            {heroAssigningKey === `${row.id}:right` ? '...' : 'Hero Der'}
          </button>
        </div>
      ),
    },
  ];

  const bulkActions: BulkAction[] = [
    {
      label: 'Eliminar seleccionados',
      icon: <Trash2 size={12} />,
      variant: 'danger',
      action: async (ids) => {
        if (!confirm(`Eliminar ${ids.length} producto(s)?`)) return;
        for (const id of ids) {
          try {
            await deleteProduct(id, effectiveToken ?? undefined);
          } catch {
            // continue with next deletion
          }
        }
        load();
      },
    },
  ];

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const displayedFrom = total === 0 ? 0 : page * pageSize + 1;
  const displayedTo = Math.min((page + 1) * pageSize, total);
  const effectiveViewMode: ViewMode = isMobileViewport ? 'cards' : viewMode;

  function PaginationControls() {
    if (total <= pageSize) return null;

    return (
      <div className="flex flex-col gap-2 px-1 pt-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-charcoal/40">
          {displayedFrom}-{displayedTo} de {total}
        </p>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="p-1.5 text-pe-charcoal/50 hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
            aria-label="Pagina anterior"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="font-sans text-[0.78rem] text-pe-charcoal/60 px-2">
            {page + 1} / {totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page + 1 >= totalPages}
            className="p-1.5 text-pe-charcoal/50 hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
            aria-label="Pagina siguiente"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>
    );
  }

  function renderCardsView() {
    if (loading) {
      return (
        <div className="flex justify-center py-16">
          <RefreshCw size={20} className="animate-spin text-pe-rose/60" />
        </div>
      );
    }

    if (products.length === 0) {
      return (
        <div className="bg-pe-white border border-pe-black/6 p-10 text-center">
          <PackageSearch size={22} className="mx-auto text-pe-charcoal/30 mb-2" />
          <p className="font-sans text-[0.82rem] text-pe-charcoal/40">No hay productos que coincidan.</p>
        </div>
      );
    }

    return (
      <>
        <div className="grid grid-cols-1 min-[460px]:grid-cols-2 xl:grid-cols-3 gap-3 sm:gap-4">
          {products.map((row) => (
            <article key={row.id} className="bg-pe-white border border-pe-black/6 shadow-xs overflow-hidden">
              <div className="relative">
                <img src={row.imageUrl} alt={row.name} className="w-full aspect-[4/5] object-cover bg-pe-cream" loading="lazy" />
                <span
                  className={[
                    'absolute top-3 left-3 px-2 py-1 text-[0.62rem] uppercase tracking-[0.12em] font-sans',
                    row.condition === 'NEW' ? 'bg-pe-black text-pe-offwhite' : 'bg-pe-rose-soft text-black border border-pe-rose/45',
                  ].join(' ')}
                >
                  {row.condition === 'NEW' ? 'Nuevo' : 'Usado'}
                </span>
                <span
                  className={[
                    'absolute top-3 right-3 px-2 py-1 text-[0.62rem] uppercase tracking-[0.12em] font-sans',
                    row.active ? 'bg-emerald-50 text-emerald-700' : 'bg-pe-cream text-pe-charcoal/50',
                  ].join(' ')}
                >
                  {row.active ? 'Activo' : 'Inactivo'}
                </span>
              </div>

              <div className="p-4">
                <p className="font-sans text-[0.65rem] uppercase tracking-[0.14em] text-pe-rose-deep/80">{row.brand}</p>
                <h3 className="font-sans text-[0.9rem] text-pe-charcoal font-medium leading-snug mt-1">{row.name}</h3>

                <div className="mt-3 flex items-center justify-between gap-3">
                  <div className="flex flex-col leading-tight">
                    {getDiscountPercent(row) !== null && (
                      <p className="font-sans text-[0.68rem] text-pe-charcoal/45 line-through">
                        {fmt(row.listPrice!.amount, row.listPrice!.currency)}
                      </p>
                    )}
                    <p className="font-sans text-[0.85rem] text-pe-charcoal">
                      {fmt(row.price.amount, row.price.currency)}
                      {getDiscountPercent(row) !== null && (
                        <span className="ml-1 text-[0.66rem] text-emerald-700">-{getDiscountPercent(row)}%</span>
                      )}
                    </p>
                  </div>
                  <p
                    className={[
                      'font-sans text-[0.75rem] uppercase tracking-[0.1em]',
                      row.stock === 0 ? 'text-red-500' : row.stock <= 2 ? 'text-amber-600' : 'text-pe-charcoal/55',
                    ].join(' ')}
                  >
                    Stock {row.stock}
                    {(row.variants?.length ?? 0) > 0 ? ` · ${row.variants!.length} comb.` : ''}
                    {formatSizesSummary(row) ? ` · ${formatSecondaryAttributeLabel(row)}: ${formatSizesSummary(row)}` : ''}
                  </p>
                </div>
                <p className="mt-2 font-sans text-[0.68rem] uppercase tracking-[0.08em] text-pe-charcoal/45">
                  Ingreso {fmtCreatedAt(row.createdAt)}
                </p>

                <div className="mt-4 space-y-2">
                  <div className="flex gap-2">
                    <button
                      onClick={() => setEditTarget(row)}
                      className="flex-1 inline-flex items-center justify-center gap-1.5 border border-pe-black/12 text-pe-charcoal text-[0.68rem] uppercase tracking-[0.1em] py-2 hover:border-pe-rose/50 hover:text-pe-rose-deep transition-colors"
                    >
                      <PencilLine size={12} />
                      Editar
                    </button>
                    <button
                      onClick={() => setDeleteConfirm(row.id)}
                      className="flex-1 inline-flex items-center justify-center gap-1.5 border border-red-300 text-red-500 text-[0.68rem] uppercase tracking-[0.1em] py-2 hover:bg-red-50 transition-colors"
                    >
                      <Trash2 size={12} />
                      Eliminar
                    </button>
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      onClick={() => void handleAssignHeroModel('left', row)}
                      disabled={heroAssigningKey !== null}
                      className="inline-flex items-center justify-center border border-pe-black/15 px-2 py-1.5 font-sans text-[0.62rem] uppercase tracking-[0.1em] text-pe-charcoal/70 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
                    >
                      {heroAssigningKey === `${row.id}:left` ? 'Asignando...' : 'Hero Izq'}
                    </button>
                    <button
                      onClick={() => void handleAssignHeroModel('right', row)}
                      disabled={heroAssigningKey !== null}
                      className="inline-flex items-center justify-center border border-pe-black/15 px-2 py-1.5 font-sans text-[0.62rem] uppercase tracking-[0.1em] text-pe-charcoal/70 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
                    >
                      {heroAssigningKey === `${row.id}:right` ? 'Asignando...' : 'Hero Der'}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
        <PaginationControls />
      </>
    );
  }

  // Active filter chips: visible feedback for parsed magic words.
  const activeChips: { label: string; onClear: () => void }[] = [];
  if (parsed.active === true) activeChips.push({ label: 'Activos', onClear: () => clearMagic('activo') });
  if (parsed.active === false) activeChips.push({ label: 'Inactivos', onClear: () => clearMagic('inactivo') });
  if (parsed.condition === 'NEW') activeChips.push({ label: 'Nuevos', onClear: () => clearMagic('nuevo') });
  if (parsed.condition === 'USED') activeChips.push({ label: 'Usados', onClear: () => clearMagic('usado') });
  if (parsed.category) {
    const cat = categories.find((c) => c.slug === parsed.category);
    activeChips.push({
      label: cat?.nameEs ?? parsed.category,
      onClear: () => clearMagic(parsed.category!),
    });
  }
  if (createdFrom) {
    activeChips.push({
      label: `Desde ${createdFrom}`,
      onClear: () => setCreatedFrom(''),
    });
  }
  if (createdTo) {
    activeChips.push({
      label: `Hasta ${createdTo}`,
      onClear: () => setCreatedTo(''),
    });
  }

  function clearMagic(token: string) {
    // Remove first matched magic-word token (case-insensitive, accent-insensitive)
    const norm = normalizeToken(token);
    const tokens = searchInput.split(/\s+/);
    const idx = tokens.findIndex((t) => {
      const n = normalizeToken(t);
      if (norm === 'activo') return ACTIVE_TRUE.has(n);
      if (norm === 'inactivo') return ACTIVE_FALSE.has(n);
      if (norm === 'nuevo') return CONDITION_NEW.has(n);
      if (norm === 'usado') return CONDITION_USED.has(n);
      return n === norm;
    });
    if (idx >= 0) {
      tokens.splice(idx, 1);
      setSearchInput(tokens.join(' ').trim());
    }
  }

  return (
    <div>
      {editTarget !== undefined && (
        <ProductForm
          product={editTarget}
          token={effectiveToken ?? undefined}
          onSave={handleSaved}
          onCancel={() => setEditTarget(undefined)}
        />
      )}

      {deleteConfirm && (
        <div className="fixed inset-0 bg-pe-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-pe-white p-6 max-w-sm w-full shadow-2xl border border-pe-black/6">
            <h3 className="font-display text-pe-black text-lg font-light mb-2">Eliminar producto?</h3>
            <p className="font-sans text-sm text-pe-charcoal/55 mb-6">Esta accion no se puede deshacer.</p>
            <div className="flex gap-3">
              <button
                onClick={() => handleDelete(deleteConfirm)}
                disabled={deleting}
                className="flex-1 inline-flex items-center justify-center gap-1.5 bg-red-600 text-white font-sans text-[0.72rem] uppercase tracking-widest py-2.5 hover:bg-red-700 disabled:opacity-50 transition-colors"
              >
                <Trash2 size={13} />
                {deleting ? 'Eliminando...' : 'Eliminar'}
              </button>
              <button
                onClick={() => setDeleteConfirm(null)}
                className="flex-1 border border-pe-black/15 font-sans text-[0.72rem] uppercase tracking-widest py-2.5 hover:border-pe-charcoal transition-colors"
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="mb-4 flex flex-col gap-2">
        {/* Unified search input */}
        <div className="flex flex-wrap items-stretch gap-2">
          <div className="relative flex-1 min-w-[260px]">
            <Search
              size={14}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-pe-charcoal/35 dark:text-[#D6C8B5]/45 pointer-events-none"
            />
            <input
              type="text"
              placeholder="Buscar por nombre, marca, descripcion, categoria... (ej: vestido activo nuevo)"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full font-sans text-[0.78rem] border border-pe-black/12 dark:border-[#3F2A2F] bg-pe-white dark:bg-[#1F1518] pl-9 pr-9 py-2 text-pe-charcoal dark:text-[#E8DCC8] placeholder:text-pe-charcoal/45 dark:placeholder:text-[#D6C8B5]/50 focus:outline-hidden focus:border-pe-rose/50 transition-colors"
            />
            {searchInput && (
              <button
                onClick={() => setSearchInput('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
                aria-label="Limpiar busqueda"
                title="Limpiar"
              >
                <X size={13} />
              </button>
            )}
          </div>

          <button
            onClick={load}
            className="p-2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            aria-label="Actualizar"
            title="Actualizar"
          >
            <RefreshCw size={15} />
          </button>
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.62rem] uppercase tracking-[0.12em] text-pe-charcoal/45">
              Fecha desde
            </span>
            <input
              type="date"
              value={createdFrom}
              onChange={(e) => setCreatedFrom(e.target.value)}
              className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-2 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.62rem] uppercase tracking-[0.12em] text-pe-charcoal/45">
              Fecha hasta
            </span>
            <input
              type="date"
              value={createdTo}
              onChange={(e) => setCreatedTo(e.target.value)}
              className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-2 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors"
            />
          </label>
          {(createdFrom || createdTo) && (
            <button
              onClick={() => {
                setCreatedFrom('');
                setCreatedTo('');
              }}
              className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] uppercase tracking-[0.1em] text-pe-charcoal/50 hover:text-pe-rose transition-colors px-2 py-2"
            >
              <X size={12} />
              Limpiar fechas
            </button>
          )}
        </div>

        {/* Active filter chips */}
        {activeChips.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-sans text-[0.62rem] uppercase tracking-[0.12em] text-pe-charcoal/40 dark:text-[#D6C8B5]/45">
              Filtros activos:
            </span>
            {activeChips.map((chip) => (
              <button
                key={chip.label}
                onClick={chip.onClear}
                className="inline-flex items-center gap-1 bg-pe-rose/10 dark:bg-[#E4B8BF]/12 border border-pe-rose/30 dark:border-[#E4B8BF]/30 text-pe-rose-deep dark:text-[#E4B8BF] font-sans text-[0.66rem] tracking-[0.06em] uppercase px-2 py-0.5 hover:bg-pe-rose/15 transition-colors"
                title={`Quitar filtro ${chip.label}`}
              >
                {chip.label}
                <X size={10} />
              </button>
            ))}
          </div>
        )}

        {heroAssignmentFeedback && (
          <div className="rounded-xs border border-emerald-500/30 bg-emerald-50 px-2.5 py-1.5 font-sans text-[0.7rem] text-emerald-700">
            {heroAssignmentFeedback}
          </div>
        )}

        {loadError && (
          <div className="rounded-xs border border-red-300/70 bg-red-50 px-3 py-2 font-sans text-[0.72rem] text-red-700">
            Error cargando productos: {loadError}
          </div>
        )}

        {/* View mode + page size + new product */}
        <div className="flex flex-wrap items-center gap-2">
          {!isMobileViewport && (
            <div className="inline-flex border border-pe-black/12 bg-pe-white">
              <button
                onClick={() => setViewMode('grid')}
                className={[
                  'inline-flex items-center gap-1.5 px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.1em] transition-colors',
                  viewMode === 'grid' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
                ].join(' ')}
                title="Vista grilla"
              >
                <Rows3 size={13} />
                Grilla
              </button>
              <button
                onClick={() => setViewMode('cards')}
                className={[
                  'inline-flex items-center gap-1.5 px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.1em] transition-colors border-l border-pe-black/12',
                  viewMode === 'cards' ? 'bg-pe-black text-pe-offwhite' : 'text-pe-charcoal/55 hover:text-pe-charcoal',
                ].join(' ')}
                title="Vista cards"
              >
                <LayoutGrid size={13} />
                Cards
              </button>
            </div>
          )}

          <label className="inline-flex items-center gap-2 font-sans text-[0.72rem] text-pe-charcoal/45">
            <span>Por página</span>
            <select
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
              className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-2 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors"
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>

          <span className="font-sans text-[0.72rem] text-pe-charcoal/35">{total} productos</span>

          <button
            onClick={() => setEditTarget(null)}
            className="ml-auto inline-flex items-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
          >
            <Plus size={13} />
            Nuevo producto
          </button>
        </div>
      </div>

      {effectiveViewMode === 'grid' ? (
        <DataTable
          columns={columns}
          data={products}
          keyField="id"
          loading={loading}
          emptyMessage="No hay productos que coincidan."
          page={page}
          pageSize={pageSize}
          total={total}
          onPageChange={setPage}
          sortKey={sortKey}
          sortDir={sortDir}
          onSort={handleSort}
          selectable
          bulkActions={bulkActions}
        />
      ) : (
        renderCardsView()
      )}
    </div>
  );
}
