import { useEffect, useRef, useState } from 'react';
import { Search, X, ArrowRight } from 'lucide-react';
import { searchProducts, getCategories } from '../../lib/api';
import type { ProductDto, CategoryDto } from '../../lib/api';

interface Props {
  locale?: string;
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
    const handler = (e: CustomEvent) => {
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

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[200] flex flex-col"
      style={{ background: 'rgba(26,26,26,0.92)', backdropFilter: 'blur(4px)' }}
    >
      {/* backdrop close */}
      <div className="absolute inset-0" onClick={() => setOpen(false)} />

      <div className="relative z-10 flex flex-col h-full max-w-3xl mx-auto w-full px-6 pt-10 pb-8">
        {/* search input row */}
        <div className="flex items-center gap-4 border-b border-[#B76E79]/40 pb-4">
          <Search size={20} strokeWidth={1.25} className="text-[#B76E79] flex-shrink-0" />
          <form onSubmit={handleSubmit} className="flex-1">
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={locale === 'es' ? 'Buscar productos, marcas...' : 'Search products, brands...'}
              className="w-full bg-transparent text-[#F8F4EF] text-2xl font-light outline-none placeholder:text-[#F8F4EF]/30 font-['Cormorant_Garamond',serif]"
            />
          </form>
          <button
            onClick={() => setOpen(false)}
            className="text-[#F8F4EF]/60 hover:text-[#F8F4EF] transition-colors"
          >
            <X size={20} strokeWidth={1.25} />
          </button>
        </div>

        {/* results */}
        <div className="flex-1 overflow-y-auto mt-8 space-y-8">
          {loading && (
            <div className="text-[#F8F4EF]/40 text-sm tracking-widest uppercase">
              {locale === 'es' ? 'Buscando...' : 'Searching...'}
            </div>
          )}

          {!loading && query && products.length === 0 && categories.length === 0 && (
            <div className="text-center mt-16">
              <p className="text-[#F8F4EF]/40 font-['Cormorant_Garamond',serif] text-xl">
                {locale === 'es' ? 'Sin resultados para' : 'No results for'}{' '}
                <span className="text-[#B76E79]">"{query}"</span>
              </p>
            </div>
          )}

          {products.length > 0 && (
            <section>
              <h3 className="text-[#F8F4EF]/40 text-xs tracking-[0.2em] uppercase mb-4">
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
                      <div className="w-12 h-12 flex-shrink-0 overflow-hidden bg-[#F8F4EF]/10">
                        <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-[#F8F4EF]/50 text-[10px] tracking-widest uppercase">{p.brand}</p>
                        <p className="text-[#F8F4EF] font-['Cormorant_Garamond',serif] text-lg leading-tight truncate group-hover:text-[#B76E79] transition-colors">
                          {p.name}
                        </p>
                      </div>
                      <span className="text-[#B76E79] font-['Cormorant_Garamond',serif]">{formatPrice(p)}</span>
                    </a>
                  </li>
                ))}
              </ul>
              <a
                href={`/${locale}/products?q=${encodeURIComponent(query)}`}
                className="mt-4 flex items-center gap-2 text-[#B76E79] text-sm hover:gap-3 transition-all"
                onClick={() => setOpen(false)}
              >
                {locale === 'es' ? 'Ver todos los resultados' : 'View all results'}
                <ArrowRight size={14} />
              </a>
            </section>
          )}

          {categories.length > 0 && (
            <section>
              <h3 className="text-[#F8F4EF]/40 text-xs tracking-[0.2em] uppercase mb-4">
                {locale === 'es' ? 'Categorías' : 'Categories'}
              </h3>
              <ul className="flex flex-wrap gap-2">
                {categories.map(c => (
                  <li key={c.id}>
                    <a
                      href={`/${locale}/categories/${c.slug}`}
                      onClick={() => setOpen(false)}
                      className="px-4 py-2 border border-[#B76E79]/30 text-[#F8F4EF]/70 text-sm hover:border-[#B76E79] hover:text-[#F8F4EF] transition-colors"
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
