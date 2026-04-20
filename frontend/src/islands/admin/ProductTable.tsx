import { useState, useEffect, useCallback } from 'react';
import { Plus, RefreshCw } from 'lucide-react';
import { getProducts, deleteProduct, type ProductDto } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import DataTable, { type Column, type BulkAction } from './DataTable';
import ProductForm from './ProductForm';

export default function ProductTable() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [products, setProducts]     = useState<ProductDto[]>([]);
  const [total, setTotal]           = useState(0);
  const [page, setPage]             = useState(0);
  const [loading, setLoading]       = useState(true);
  const [sortKey, setSortKey]       = useState<string | undefined>(undefined);
  const [sortDir, setSortDir]       = useState<'asc' | 'desc'>('asc');
  const [editTarget, setEditTarget] = useState<ProductDto | null | undefined>(undefined);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [deleting, setDeleting]     = useState(false);
  const [filterCondition, setFilterCondition] = useState('');
  const [filterBrand, setFilterBrand] = useState('');

  const PAGE_SIZE = 20;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getProducts({
        page,
        size: PAGE_SIZE,
        condition: (filterCondition as 'NEW' | 'USED') || undefined,
        brand: filterBrand || undefined,
      });
      setProducts(res.content);
      setTotal(res.totalElements);
    } finally {
      setLoading(false);
    }
  }, [page, filterCondition, filterBrand]);

  useEffect(() => { load(); }, [load]);

  function handleSort(key: string) {
    if (sortKey === key) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  async function handleDelete(id: string) {
    if (!effectiveToken) {
      alert('Tu sesiÃ³n de administraciÃ³n expirÃ³. Vuelve a iniciar sesiÃ³n.');
      return;
    }
    setDeleting(true);
    try {
      await deleteProduct(id, effectiveToken);
      setProducts(prev => prev.filter(p => p.id !== id));
      setTotal(prev => prev - 1);
    } catch {
      alert('Error al eliminar el producto.');
    } finally {
      setDeleting(false);
      setDeleteConfirm(null);
    }
  }

  function handleSaved(saved: ProductDto) {
    setProducts(prev => {
      const idx = prev.findIndex(p => p.id === saved.id);
      if (idx >= 0) { const next = [...prev]; next[idx] = saved; return next; }
      return [saved, ...prev];
    });
    if (editTarget === null) setTotal(t => t + 1);
    setEditTarget(undefined);
  }

  const fmt = (amount: number, currency: string) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount);

  const columns: Column<ProductDto>[] = [
    {
      key: 'image',
      header: '',
      width: '56px',
      render: row => (
        <img src={row.imageUrl} alt={row.name} width={36} height={44}
          className="w-9 h-11 object-cover bg-pe-cream" loading="lazy" />
      ),
    },
    {
      key: 'name',
      header: 'Nombre',
      sortable: true,
      render: row => (
        <span className="block font-sans text-[0.82rem] font-medium text-pe-charcoal leading-snug max-w-[180px] truncate">
          {row.name}
        </span>
      ),
    },
    {
      key: 'brand',
      header: 'Marca',
      sortable: true,
      render: row => (
        <span className="font-sans text-[0.72rem] tracking-[0.1em] uppercase text-pe-rose-deep/80">{row.brand}</span>
      ),
    },
    {
      key: 'condition',
      header: 'Condición',
      render: row => (
        <span className={[
          'font-sans text-[0.65rem] tracking-[0.12em] uppercase px-2 py-0.5',
          row.condition === 'NEW'
            ? 'bg-pe-black text-pe-offwhite'
            : 'bg-pe-cream text-pe-charcoal/60 border border-pe-black/12',
        ].join(' ')}>
          {row.condition === 'NEW' ? 'Nuevo' : 'Usado'}
        </span>
      ),
    },
    {
      key: 'price',
      header: 'Precio',
      sortable: true,
      render: row => (
        <span className="font-sans text-[0.82rem] text-pe-charcoal">
          {fmt(row.price.amount, row.price.currency)}
        </span>
      ),
    },
    {
      key: 'stock',
      header: 'Stock',
      width: '64px',
      render: row => (
        <span className={[
          'font-sans text-[0.82rem]',
          row.stock === 0 ? 'text-red-500' : row.stock <= 2 ? 'text-amber-600' : 'text-pe-charcoal',
        ].join(' ')}>
          {row.stock}
        </span>
      ),
    },
    {
      key: 'active',
      header: 'Activo',
      width: '60px',
      render: row => (
        <span className={row.active ? 'text-green-600 text-[0.82rem]' : 'text-pe-charcoal/25 text-[0.82rem]'}>
          {row.active ? '✓' : '✗'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      width: '120px',
      render: row => (
        <div className="flex gap-3">
          <button
            onClick={e => { e.stopPropagation(); setEditTarget(row); }}
            className="font-sans text-[0.68rem] uppercase tracking-[0.1em] text-pe-charcoal/50 hover:text-pe-rose-deep transition-colors"
          >
            Editar
          </button>
          <button
            onClick={e => { e.stopPropagation(); setDeleteConfirm(row.id); }}
            className="font-sans text-[0.68rem] uppercase tracking-[0.1em] text-pe-charcoal/50 hover:text-red-500 transition-colors"
          >
            Eliminar
          </button>
        </div>
      ),
    },
  ];

  const bulkActions: BulkAction[] = [
    {
      label: 'Eliminar seleccionados',
      variant: 'danger',
      action: async (ids) => {
        if (!confirm(`¿Eliminar ${ids.length} producto(s)?`)) return;
        for (const id of ids) {
          try { await deleteProduct(id, effectiveToken ?? undefined); } catch { /* continue */ }
        }
        load();
      },
    },
  ];

  return (
    <div>
      {/* Product form modal */}
      {editTarget !== undefined && (
        <ProductForm product={editTarget} token={effectiveToken ?? undefined} onSave={handleSaved} onCancel={() => setEditTarget(undefined)} />
      )}

      {/* Delete confirm */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-pe-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-pe-white p-6 max-w-sm w-full shadow-2xl border border-pe-black/6">
            <h3 className="font-display text-pe-black text-lg font-light mb-2">¿Eliminar producto?</h3>
            <p className="font-sans text-sm text-pe-charcoal/55 mb-6">Esta acción no se puede deshacer.</p>
            <div className="flex gap-3">
              <button
                onClick={() => handleDelete(deleteConfirm)}
                disabled={deleting}
                className="flex-1 bg-red-600 text-white font-sans text-[0.72rem] uppercase tracking-widest py-2.5 hover:bg-red-700 disabled:opacity-50 transition-colors"
              >
                {deleting ? 'Eliminando…' : 'Eliminar'}
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

      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <select
          value={filterCondition}
          onChange={e => { setFilterCondition(e.target.value); setPage(0); }}
          className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-3 py-2 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors"
        >
          <option value="">Todas las condiciones</option>
          <option value="NEW">Nuevo</option>
          <option value="USED">Usado</option>
        </select>
        <input
          type="text"
          placeholder="Filtrar por marca…"
          value={filterBrand}
          onChange={e => { setFilterBrand(e.target.value); setPage(0); }}
          className="font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-3 py-2 text-pe-charcoal placeholder:text-pe-charcoal/30 focus:outline-none focus:border-pe-rose/50 transition-colors"
        />
        <button
          onClick={load}
          className="p-2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
          aria-label="Actualizar"
          title="Actualizar"
        >
          <RefreshCw size={15} />
        </button>
        <span className="font-sans text-[0.72rem] text-pe-charcoal/35 ml-1">{total} productos</span>

        <button
          onClick={() => setEditTarget(null)}
          className="ml-auto flex items-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nuevo producto
        </button>
      </div>

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
    </div>
  );
}
