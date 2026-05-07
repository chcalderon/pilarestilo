import { useEffect, useMemo, useState } from 'react';
import { useCartStore } from '../../lib/cartStore';
import type { Locale } from '../../i18n/index';
import type { ProductVariantDto } from '../../lib/api';
import { summarizeVariantSizes } from '../../lib/productVariants';

interface Props {
  productId: string;
  name: string;
  brand: string;
  price: { amount: number; currency: string };
  imageUrl: string;
  condition: 'NEW' | 'USED';
  stock: number;
  locale: Locale;
  variants?: ProductVariantDto[];
}

function slugify(input: string) {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'variant';
}

export default function ProductVariantSelector({
  productId,
  name,
  brand,
  price,
  imageUrl,
  condition,
  stock,
  locale,
  variants,
}: Props) {
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);

  const labels = {
    selectColor: locale === 'es' ? 'Color' : 'Color',
    selectSize: locale === 'es' ? 'Talla' : 'Size',
    availableSizes: locale === 'es' ? 'Tallas disponibles' : 'Available sizes',
    addToCart: locale === 'es' ? 'Agregar al Carrito' : 'Add to Cart',
    outOfStock: locale === 'es' ? 'Sin Stock' : 'Out of Stock',
    added: locale === 'es' ? 'Agregado' : 'Added',
    chooseVariant: locale === 'es' ? 'Selecciona color y talla' : 'Select color and size',
  };

  const normalizedVariants = useMemo(
    () =>
      (variants ?? []).filter((v) => Boolean(v.color?.trim()) && Boolean(v.size?.trim())),
    [variants]
  );

  const hasVariants = normalizedVariants.length > 0;
  const allSizesSummary = useMemo(() => summarizeVariantSizes(normalizedVariants), [normalizedVariants]);
  const colorOptions = useMemo(() => {
    const seen = new Set<string>();
    const colors: string[] = [];
    for (const variant of normalizedVariants) {
      const color = variant.color.trim();
      if (!seen.has(color)) {
        seen.add(color);
        colors.push(color);
      }
    }
    return colors;
  }, [normalizedVariants]);

  const [selectedColor, setSelectedColor] = useState<string | null>(null);
  const [selectedSize, setSelectedSize] = useState<string | null>(null);

  useEffect(() => {
    if (!hasVariants) return;
    if (!selectedColor || !colorOptions.includes(selectedColor)) {
      setSelectedColor(colorOptions[0] ?? null);
    }
  }, [hasVariants, colorOptions, selectedColor]);

  const sizeOptions = useMemo(() => {
    if (!selectedColor) return [];
    return normalizedVariants.filter((v) => v.color.trim() === selectedColor);
  }, [normalizedVariants, selectedColor]);
  const selectedColorSizesSummary = useMemo(() => {
    const unique = Array.from(new Set(sizeOptions.map((v) => v.size)));
    return unique.join('-');
  }, [sizeOptions]);

  useEffect(() => {
    if (!hasVariants) return;
    if (!selectedSize || !sizeOptions.some((v) => v.size === selectedSize && v.stock > 0)) {
      const firstInStock = sizeOptions.find((v) => v.stock > 0);
      setSelectedSize(firstInStock?.size ?? null);
    }
  }, [hasVariants, sizeOptions, selectedSize]);

  const selectedVariant = useMemo(
    () =>
      normalizedVariants.find(
        (v) => v.color.trim() === selectedColor && v.size === selectedSize
      ) ?? null,
    [normalizedVariants, selectedColor, selectedSize]
  );

  const depletedByGlobalStock = stock <= 0;
  const outOfStock = depletedByGlobalStock || (hasVariants ? !selectedVariant || selectedVariant.stock === 0 : stock === 0);
  const canAdd = hasVariants ? Boolean(selectedVariant) && !outOfStock : !outOfStock;

  const handleAdd = () => {
    if (!canAdd || added) return;

    if (!hasVariants || !selectedVariant) {
      addItem({
        id: productId,
        productId,
        name,
        brand,
        price,
        imageUrl,
        condition,
      });
    } else {
      const color = selectedVariant.color.trim();
      const size = selectedVariant.size;
      const lineId = `${productId}::${slugify(color)}::${size}`;
      addItem({
        id: lineId,
        productId,
        name,
        brand,
        price,
        imageUrl,
        condition,
        variantColor: color,
        variantSize: size,
        variantLabel: `${labels.selectColor}: ${color} / ${labels.selectSize}: ${size}`,
      });
    }

    setAdded(true);
    window.setTimeout(() => setAdded(false), 1800);
  };

  return (
    <div className="space-y-4">
      {hasVariants && (
        <>
          <div>
            <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-2">
              {labels.selectColor}
            </p>
            <div className="flex flex-wrap gap-2">
              {colorOptions.map((color) => {
                const isSelected = selectedColor === color;
                return (
                  <button
                    key={color}
                    type="button"
                    onClick={() => setSelectedColor(color)}
                    disabled={depletedByGlobalStock}
                    className={[
                      'px-3 py-2 text-xs tracking-wider border transition-colors',
                      depletedByGlobalStock
                        ? 'border-[#EDE3D8] text-[#3A3A3A]/25 cursor-not-allowed'
                        : isSelected
                        ? 'border-[#B76E79] bg-[#B76E79] text-white'
                        : 'border-[#3A3A3A]/30 text-[#1A1A1A] hover:border-[#B76E79] hover:text-[#B76E79]',
                    ].join(' ')}
                  >
                    {color}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-2">
              {labels.selectSize}
            </p>
            {allSizesSummary && (
              <p className="text-[10px] tracking-[0.08em] uppercase text-[#3A3A3A]/45 mb-2">
                {labels.availableSizes}: {selectedColorSizesSummary || allSizesSummary}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              {sizeOptions.map((variant) => {
                const isSelected = selectedSize === variant.size;
                const noStock = variant.stock === 0;
                return (
                  <button
                    key={`${variant.color}-${variant.size}`}
                    type="button"
                    disabled={depletedByGlobalStock || noStock}
                    onClick={() => setSelectedSize(variant.size)}
                    className={[
                      'min-w-12 px-2 h-12 flex items-center justify-center text-xs tracking-wide border transition-colors',
                      depletedByGlobalStock || noStock
                        ? 'border-[#EDE3D8] text-[#3A3A3A]/25 cursor-not-allowed line-through'
                        : isSelected
                        ? 'border-[#B76E79] bg-[#B76E79] text-white'
                        : 'border-[#3A3A3A]/30 text-[#1A1A1A] hover:border-[#B76E79] hover:text-[#B76E79]',
                    ].join(' ')}
                  >
                    {variant.size}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}

      {hasVariants && !selectedVariant && (
        <p className="text-xs text-[#8E4F58]">{labels.chooseVariant}</p>
      )}

      <button
        type="button"
        onClick={handleAdd}
        disabled={!canAdd}
        aria-label={outOfStock ? labels.outOfStock : labels.addToCart}
        className={[
          'w-full font-sans text-xs tracking-widest uppercase px-4 py-2.5 transition-all duration-200',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold focus-visible:ring-offset-1',
          !canAdd
            ? 'bg-pe-black/10 text-pe-black/30 cursor-not-allowed'
            : added
            ? 'bg-pe-gold/80 text-pe-black'
            : 'bg-pe-gold text-pe-black hover:bg-opacity-90 active:scale-95',
        ].join(' ')}
      >
        {outOfStock ? labels.outOfStock : added ? labels.added : labels.addToCart}
      </button>
    </div>
  );
}
