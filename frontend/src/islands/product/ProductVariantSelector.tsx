import { useEffect, useMemo, useState } from 'react';
import { useCartStore } from '../../lib/cartStore';
import type { Locale } from '../../i18n/index';
import type { CategoryVariantFieldConfigDto, ProductVariantDto } from '../../lib/api';
import {
  buildVariantSchema,
  getPrimaryAttribute,
  getSecondaryAttribute,
  summarizeVariantAttributeValues,
  toVariantAttributeRecord,
} from '../../lib/variantSchema';

interface Props {
  readonly productId: string;
  readonly name: string;
  readonly brand: string;
  readonly price: { amount: number; currency: string };
  readonly imageUrl: string;
  readonly condition: 'NEW' | 'USED';
  readonly stock: number;
  readonly locale: Locale;
  readonly variantFieldConfig?: CategoryVariantFieldConfigDto | null;
  readonly variants?: ProductVariantDto[];
}

function slugify(input: string) {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'variant';
}

const OPTION_SELECTED_CLASS = 'border-[#B76E79] bg-[#B76E79] text-white';
const OPTION_DEFAULT_CLASS = 'border-[#3A3A3A]/30 text-[#1A1A1A] hover:border-[#B76E79] hover:text-[#B76E79]';

function optionButtonClass(disabled: boolean, isSelected: boolean, disabledClass: string) {
  if (disabled) return disabledClass;
  return isSelected ? OPTION_SELECTED_CLASS : OPTION_DEFAULT_CLASS;
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
  variantFieldConfig,
  variants,
}: Props) {
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);
  const schema = useMemo(() => buildVariantSchema(variantFieldConfig ?? null), [variantFieldConfig]);
  const primaryAttribute = useMemo(() => getPrimaryAttribute(schema), [schema]);
  const secondaryAttribute = useMemo(() => getSecondaryAttribute(schema), [schema]);

  const labels = {
    selectPrimary: primaryAttribute.label,
    selectSecondary: secondaryAttribute.label,
    availableSecondary: locale === 'es'
      ? `${secondaryAttribute.label}s disponibles`
      : `Available ${secondaryAttribute.label.toLowerCase()}s`,
    addToCart: locale === 'es' ? 'Agregar al Carrito' : 'Add to Cart',
    outOfStock: locale === 'es' ? 'Sin Stock' : 'Out of Stock',
    added: locale === 'es' ? 'Agregado' : 'Added',
    chooseVariant: locale === 'es'
      ? `Selecciona ${primaryAttribute.label.toLowerCase()} y ${secondaryAttribute.label.toLowerCase()}`
      : `Select ${primaryAttribute.label.toLowerCase()} and ${secondaryAttribute.label.toLowerCase()}`,
  };

  const normalizedVariants = useMemo(
    () =>
      (variants ?? [])
        .map((variant) => ({
          variant,
          record: toVariantAttributeRecord(variant, schema),
        }))
        .filter(
          ({ record }) =>
            Boolean(record[primaryAttribute.code]?.trim()) && Boolean(record[secondaryAttribute.code]?.trim()),
        ),
    [variants, schema, primaryAttribute.code, secondaryAttribute.code]
  );

  const hasVariants = normalizedVariants.length > 0;
  const allSecondarySummary = useMemo(
    () => summarizeVariantAttributeValues(
      normalizedVariants.map(({ variant }) => variant),
      schema,
      secondaryAttribute.code,
    ),
    [normalizedVariants, schema, secondaryAttribute.code]
  );
  const primaryOptions = useMemo(() => {
    const seen = new Set<string>();
    const values: string[] = [];
    for (const item of normalizedVariants) {
      const value = item.record[primaryAttribute.code]?.trim();
      if (value && !seen.has(value)) {
        seen.add(value);
        values.push(value);
      }
    }
    return values;
  }, [normalizedVariants, primaryAttribute.code]);

  const [selectedPrimary, setSelectedPrimary] = useState<string | null>(null);
  const [selectedSecondary, setSelectedSecondary] = useState<string | null>(null);

  useEffect(() => {
    if (!hasVariants) return;
    if (!selectedPrimary || !primaryOptions.includes(selectedPrimary)) {
      setSelectedPrimary(primaryOptions[0] ?? null);
    }
  }, [hasVariants, primaryOptions, selectedPrimary]);

  const secondaryOptions = useMemo(() => {
    if (!selectedPrimary) return [];
    return normalizedVariants.filter((item) => item.record[primaryAttribute.code] === selectedPrimary);
  }, [normalizedVariants, selectedPrimary, primaryAttribute.code]);
  const selectedPrimarySecondarySummary = useMemo(() => {
    const unique = Array.from(new Set(secondaryOptions.map((item) => item.record[secondaryAttribute.code])));
    return unique.join(secondaryAttribute.summaryJoiner ?? ' / ');
  }, [secondaryOptions, secondaryAttribute]);

  useEffect(() => {
    if (!hasVariants) return;
    if (
      !selectedSecondary ||
      !secondaryOptions.some((item) => item.record[secondaryAttribute.code] === selectedSecondary && item.variant.stock > 0)
    ) {
      const firstInStock = secondaryOptions.find((item) => item.variant.stock > 0);
      setSelectedSecondary(firstInStock?.record[secondaryAttribute.code] ?? null);
    }
  }, [hasVariants, secondaryOptions, selectedSecondary, secondaryAttribute.code]);

  const selectedVariantEntry = useMemo(
    () =>
      normalizedVariants.find(
        (item) =>
          item.record[primaryAttribute.code] === selectedPrimary &&
          item.record[secondaryAttribute.code] === selectedSecondary
      ) ?? null,
    [normalizedVariants, selectedPrimary, selectedSecondary, primaryAttribute.code, secondaryAttribute.code]
  );

  const depletedByGlobalStock = stock <= 0;
  const outOfStock = depletedByGlobalStock
    || (hasVariants ? !selectedVariantEntry || selectedVariantEntry.variant.stock === 0 : stock === 0);
  const canAdd = hasVariants ? Boolean(selectedVariantEntry) && !outOfStock : !outOfStock;

  const handleAdd = () => {
    if (!canAdd || added) return;

    if (!hasVariants || !selectedVariantEntry) {
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
      const primaryValue = selectedVariantEntry.record[primaryAttribute.code];
      const secondaryValue = selectedVariantEntry.record[secondaryAttribute.code];
      const lineId = `${productId}::${slugify(primaryValue)}::${secondaryValue}`;
      addItem({
        id: lineId,
        productId,
        name,
        brand,
        price,
        imageUrl,
        condition,
        variantColor: primaryValue,
        variantSize: secondaryValue,
        variantLabel: `${labels.selectPrimary}: ${primaryValue} / ${labels.selectSecondary}: ${secondaryValue}`,
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
              {labels.selectPrimary}
            </p>
            <div className="flex flex-wrap gap-2">
              {primaryOptions.map((primaryValue) => {
                const isSelected = selectedPrimary === primaryValue;
                return (
                  <button
                    key={primaryValue}
                    type="button"
                    onClick={() => setSelectedPrimary(primaryValue)}
                    disabled={depletedByGlobalStock}
                    className={[
                      'px-3 py-2 text-xs tracking-wider border transition-colors',
                      optionButtonClass(
                        depletedByGlobalStock, isSelected,
                        'border-[#EDE3D8] text-[#3A3A3A]/25 cursor-not-allowed'),
                    ].join(' ')}
                  >
                    {primaryValue}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-2">
              {labels.selectSecondary}
            </p>
            {allSecondarySummary && (
              <p className="text-[10px] tracking-[0.08em] uppercase text-[#3A3A3A]/45 mb-2">
                {labels.availableSecondary}: {selectedPrimarySecondarySummary || allSecondarySummary}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              {secondaryOptions.map((item) => {
                const value = item.record[secondaryAttribute.code];
                const isSelected = selectedSecondary === value;
                const noStock = item.variant.stock === 0;
                return (
                  <button
                    key={`${item.variant.color}-${item.variant.size}`}
                    type="button"
                    disabled={depletedByGlobalStock || noStock}
                    onClick={() => setSelectedSecondary(value)}
                    className={[
                      'min-w-12 px-2 h-12 flex items-center justify-center text-xs tracking-wide border transition-colors',
                      optionButtonClass(
                        depletedByGlobalStock || noStock, isSelected,
                        'border-[#EDE3D8] text-[#3A3A3A]/25 cursor-not-allowed line-through'),
                    ].join(' ')}
                  >
                    {value}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}

      {hasVariants && !selectedVariantEntry && (
        <p className="text-xs text-[#8E4F58]">{labels.chooseVariant}</p>
      )}

      {(() => {
        let addButtonClass: string;
        if (!canAdd) {
          addButtonClass = 'bg-pe-black/10 text-pe-black/30 cursor-not-allowed';
        } else if (added) {
          addButtonClass = 'bg-pe-gold/80 text-pe-on-light';
        } else {
          addButtonClass = 'bg-pe-gold text-pe-on-light hover:bg-pe-gold/90 active:scale-95';
        }
        let addButtonLabel: string;
        if (outOfStock) {
          addButtonLabel = labels.outOfStock;
        } else if (added) {
          addButtonLabel = labels.added;
        } else {
          addButtonLabel = labels.addToCart;
        }
        return (
          <button
            type="button"
            onClick={handleAdd}
            disabled={!canAdd}
            aria-label={outOfStock ? labels.outOfStock : labels.addToCart}
            className={[
              'w-full font-sans text-xs tracking-widest uppercase px-4 py-2.5 transition-all duration-200',
              'focus:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-gold focus-visible:ring-offset-1',
              addButtonClass,
            ].join(' ')}
          >
            {addButtonLabel}
          </button>
        );
      })()}
    </div>
  );
}
