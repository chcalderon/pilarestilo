import { useState } from 'react';
import type { SizeStockDto } from '../../lib/api';

interface Props {
  sizeStocks: SizeStockDto[];
  locale?: string;
  onSelect?: (size: string | null) => void;
}

export default function SizeSelector({ sizeStocks, locale = 'es', onSelect }: Props) {
  const [selected, setSelected] = useState<string | null>(null);

  if (!sizeStocks || sizeStocks.length === 0) return null;

  const isUnico = sizeStocks.length === 1 && sizeStocks[0].size === 'UNICO';
  if (isUnico) return null;

  const handleSelect = (size: string, stock: number) => {
    if (stock === 0) return;
    const next = selected === size ? null : size;
    setSelected(next);
    onSelect?.(next);
  };

  return (
    <div>
      <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-3">
        {locale === 'es' ? 'Talle' : 'Size'}
        {selected && <span className="ml-2 text-[#B76E79]">{selected}</span>}
      </p>
      <div className="flex flex-wrap gap-2">
        {sizeStocks.map(({ size, stock }) => {
          const outOfStock = stock === 0;
          const lowStock = stock > 0 && stock < 3;
          const isSelected = selected === size;

          return (
            <div key={size} className="relative">
              <button
                type="button"
                disabled={outOfStock}
                onClick={() => handleSelect(size, stock)}
                className={`w-12 h-12 flex items-center justify-center text-xs tracking-wide border transition-all duration-150 ${
                  outOfStock
                    ? 'border-[#EDE3D8] text-[#3A3A3A]/25 cursor-not-allowed line-through'
                    : isSelected
                    ? 'border-[#B76E79] bg-[#B76E79] text-white'
                    : 'border-[#3A3A3A]/30 text-[#1A1A1A] hover:border-[#B76E79] hover:text-[#B76E79]'
                }`}
              >
                {size}
              </button>
              {lowStock && !outOfStock && (
                <span className="absolute -bottom-4 left-0 right-0 text-center text-[9px] text-[#B76E79] whitespace-nowrap leading-none">
                  {locale === 'es' ? `Últimas ${stock}` : `Last ${stock}`}
                </span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
