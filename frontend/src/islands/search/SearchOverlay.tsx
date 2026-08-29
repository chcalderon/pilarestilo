import { useEffect, useRef, useState } from 'react';
import { Search, X, ArrowRight } from 'lucide-react';
import { searchProducts, getCategories } from '../../lib/api';
import type { ProductDto, CategoryDto } from '../../lib/api';

interface Props {
  readonly locale?: string;
}

export default function SearchOverlay({ locale = 'es' }: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const handler = () => {
      setOpen(true);
    };
    window.addEventListener('open-search', handler as EventListener);
    return () => window.removeEventListener('open-search', handler as EventListener);
  }, []);

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setProducts([]);
      setCategories([]);
    }
  }, [open]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!query.trim()) {
      setProducts([]);
      setCategories([]);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const [prodRes, allCats] = await Promise.all([
          searchProducts(query, 0, 6),
          getCategories(),
        ]);
        setProducts(prodRes.content);
        const lower = query.toLowerCase();
        setCategories(
          allCats.filter(
            c => c.nameEs.toLowerCase().includes(lower) || c.nameEn.toLowerCase().includes(lower)
          ).slice(0, 4)
        );
      } finally {
        setLoading(false);
      }
    }, 250);
  }, [query]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    window.location.href = `/${locale}/products?q=${encodeURIComponent(query)}`;
  };

  const formatPrice = (p: ProductDto) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: p.price.currency ?? 'CLP', maximumFractionDigits: 0 }).format(p.price.amount);
  const hasDiscount = (p: ProductDto) =>
    !!p.listPrice && p.listPrice.currency === p.price.currency && p.listPrice.amount > p.price.amount;
  const formatListPrice = (p: ProductDto) =>
    p.listPrice
      ? new Intl.NumberFormat('es-CL', { style: 'currency', currency: p.listPrice.currency ?? 'CLP', maximumFractionDigits: 0 }).format(p.listPrice.amount)
      : '';

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[200] flex flex-col"
      style={{ background: 'rgba(26,26,26,0.92)', backdropFilter: 'blur(4px)' }}
    >
      {/* backdrop close */}
      <button
        type="button"
        aria-label="Cerrar la busqueda"
        className="absolute inset-0 cursor-default"
        onClick={() => setOpen(false)}
      />

      <div className="relative z-10 flex flex-col h-full max-w-3xl mx-auto w-full px-6 pt-10 pb-8">
        {/* search input row */}
        <div className="flex items-center gap-4 border-b border-pe-rose/40 pb-4">
          <Search size={20} strokeWidth={1.25} className="text-pe-rose flex-shrink-0" />
          <form onSubmit={handleSubmit} className="flex-1">
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={locale === 'es' ? 'Buscar productos, marcas...' : 'Search products, brands...'}
              className="w-full bg-transparent text-pe-on-dark text-2xl font-light outline-hidden placeholder:text-pe-on-dark/30 font-display"
            />
          </form>
          <button
            type="button"
            onClick={() => setOpen(false)}
            className="text-pe-on-dark/60 hover:text-pe-on-dark transition-colors"
          >
            <X size={20} strokeWidth={1.25} />
          </button>
        </div>

        {/* results */}
        <div className="flex-1 overflow-y-auto mt-8 space-y-8">
          {loading && (
            <div className="text-pe-on-dark/40 text-sm tracking-widest uppercase">
              {locale === 'es' ? 'Buscando...' : 'Searching...'}
            </div>
          )}

          {!loading && query && products.length === 0 && categories.length === 0 && (
            <div className="text-center mt-16">
              <p className="text-pe-on-dark/40 font-display text-xl">
                {locale === 'es' ? 'Sin resultados para' : 'No results for'}{' '}
                <span className="text-pe-rose">&quot;{query}&quot;</span>
              </p>
            </div>
          )}

          {products.length > 0 && (
            <section>
              <h3 className="text-pe-on-dark/40 text-xs tracking-[0.2em] uppercase mb-4">
                {locale === 'es' ? 'Productos' : 'Products'}
              </h3>
              <ul className="space-y-3">
                {products.map(p => (
                  <li key={p.id}>
                    <a
                      href={`/${locale}/products/${p.id}`}
                      onClick={() => setOpen(false)}
                      className="flex items-center gap-4 group"
                    >
                      <div className="w-12 h-12 flex-shrink-0 overflow-hidden bg-pe-on-dark/10">
                        <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-pe-on-dark/50 text-[10px] tracking-widest uppercase">{p.brand}</p>
                        <p className="text-pe-on-dark font-display text-lg leading-tight truncate group-hover:text-pe-rose transition-colors">
                          {p.name}
                        </p>
                      </div>
                      <span className="flex flex-col items-end">
                        {hasDiscount(p) && (
                          <span className="text-pe-on-dark/35 text-[0.7rem] line-through">{formatListPrice(p)}</span>
                        )}
                        <span className="text-pe-rose font-display">{formatPrice(p)}</span>
                      </span>
                    </a>
                  </li>
                ))}
              </ul>
              <a
                href={`/${locale}/products?q=${encodeURIComponent(query)}`}
                className="mt-4 flex items-center gap-2 text-pe-rose text-sm hover:gap-3 transition-all"
                onClick={() => setOpen(false)}
              >
                {locale === 'es' ? 'Ver todos los resultados' : 'View all results'}
                <ArrowRight size={14} />
              </a>
            </section>
          )}

          {categories.length > 0 && (
            <section>
              <h3 className="text-pe-on-dark/40 text-xs tracking-[0.2em] uppercase mb-4">
                {locale === 'es' ? 'Categorías' : 'Categories'}
              </h3>
              <ul className="flex flex-wrap gap-2">
                {categories.map(c => (
                  <li key={c.id}>
                    <a
                      href={`/${locale}/categories/${c.slug}`}
                      onClick={() => setOpen(false)}
                      className="px-4 py-2 border border-pe-rose/30 text-pe-on-dark/70 text-sm hover:border-pe-rose hover:text-pe-on-dark transition-colors"
                    >
                      {locale === 'es' ? c.nameEs : c.nameEn}
                    </a>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}
