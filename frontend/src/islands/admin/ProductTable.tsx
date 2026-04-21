import { useState, useEffect, useCallback } from 'react';
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
} from 'lucide-react';
import { getProducts, deleteProduct, getCategories, type ProductDto, type CategoryDto } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column, type BulkAction } from './DataTable';
import ProductForm from './ProductForm';

type ViewMode = 'grid' | 'cards';

const PAGE_SIZE = 20;
const VIEW_MODE_KEY = 'pe-admin-products-view';

export default function ProductTable() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [sortKey, setSortKey] = useState<string | undefined>(undefined);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
  const [editTarget, setEditTarget] = useState<ProductDto | null | undefined>(undefined);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [filterCondition, setFilterCondition] = useState('');
  const [filterBrand, setFilterBrand] = useState('');
  const [filterCategory, setFilterCategory] = useState('');
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [viewMode, setViewMode] = useState<ViewMode>('grid');

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const saved = window.localStorage.getItem(VIEW_MODE_KEY);
    if (saved === 'grid' || saved === 'cards') {
      setViewMode(saved);
      return;
    }
    if (window.innerWidth < 768) {
      setViewMode('cards');
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(VIEW_MODE_KEY, viewMode);
  }, [viewMode]);

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

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getProducts({
        page,
        size: PAGE_SIZE,
        condition: (filterCondition as 'NEW' | 'USED') || undefined,
        brand: filterBrand || undefined,
        category: filterCategory || undefined,
      });
      setProducts(res.content);
      setTotal(res.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, filterCondition, filterBrand, filterCategory]);

  useEffect(() => {
    load();
  }, [load]);

  function handleSort(key: string) {
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
      render: (row) => (
        <span
          className={[
            'font-sans text-[0.82rem]',
            row.stock === 0 ? 'text-red-500' : row.stock <= 2 ? 'text-amber-600' : 'text-pe-charcoal',
          ].join(' ')}
        >
          {row.stock}
        </span>
      ),
    },
    {
      key: 'active',
      header: 'Activo',
      width: '84px',
      render: (row) => (
        <span
          className={[
            'inline-flex items-center rounded px-2 py-0.5 font-sans text-[0.65rem] uppercase tracking-[0.1em]',
            row.active ? 'bg-emerald-50 text-emerald-700' : 'bg-pe-cream text-pe-charcoal/45',
          ].join(' ')}
        >
          {row.active ? 'Si' : 'No'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '180px',
      render: (row) => (
        <div className="flex gap-3">
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

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const displayedFrom = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const displayedTo = Math.min((page + 1) * PAGE_SIZE, total);

  function PaginationControls() {
    if (total <= PAGE_SIZE) return null;

    return (
      <div className="flex items-center justify-between px-1 pt-3">
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
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {products.map((row) => (
            <article key={row.id} className="bg-pe-white border border-pe-black/6 shadow-sm overflow-hidden">
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
                  </p>
                </div>

                <div className="mt-4 flex gap-2">
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
              </div>
            </article>
          ))}
        </div>
        <PaginationControls />
      </>
    );
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

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <select
          value={filterCondition}
          onChange={(e) => {
            setFilterCondition(e.target.value);
            setPage(0);
          }}
          className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-3 py-2 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors"
        >
          <option value="">Todas las condiciones</option>
          <option value="NEW">Nuevo</option>
          <option value="USED">Usado</option>
        </select>

        <input
          type="text"
          placeholder="Filtrar por marca..."
          value={filterBrand}
          onChange={(e) => {
            setFilterBrand(e.target.value);
            setPage(0);
          }}
          className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-3 py-2 text-pe-charcoal placeholder:text-pe-charcoal/30 focus:outline-none focus:border-pe-rose/50 transition-colors"
        />

        <select
          value={filterCategory}
          onChange={(e) => {
            setFilterCategory(e.target.value);
            setPage(0);
          }}
          className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-3 py-2 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors"
        >
          <option value="">Todas las categorias</option>
          {categories.map((cat) => (
            <option key={cat.id} value={cat.slug}>
              {cat.nameEs}
            </option>
          ))}
        </select>

        <button
          onClick={load}
          className="p-2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
          aria-label="Actualizar"
          title="Actualizar"
        >
          <RefreshCw size={15} />
        </button>

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

        <span className="font-sans text-[0.72rem] text-pe-charcoal/35 ml-1">{total} productos</span>

        <button
          onClick={() => setEditTarget(null)}
          className="ml-auto inline-flex items-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nuevo producto
        </button>
      </div>

      {viewMode === 'grid' ? (
        <DataTable
          columns={columns}
          data={products}
          keyField="id"
          loading={loading}
          emptyMessage="No hay productos que coincidan."
          page={page}
          pageSize={PAGE_SIZE}
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
