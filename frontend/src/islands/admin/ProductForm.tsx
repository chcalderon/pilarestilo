import { useState, useEffect, useMemo, useRef } from 'react';
import { Loader2, Save, X, ChevronDown, ChevronRight, FolderOpen, Folder, Tag } from 'lucide-react';
import {
  assignHeroModelFromProduct,
  createProduct,
  updateProduct,
  getCategories,
  getSystemSettings,
  inferSingleProductAi,
  transformSingleProductAiImage,
  type ProductDto,
  type CreateProductRequest,
  type CategoryDto,
  type ProductVariantDto,
} from '../../lib/api';
import ImageDropzone from './ImageDropzone';
import {
  createEmptyVariantSelections,
  getAttributeValue,
  getAttributeValues,
  getPrimaryAttribute,
  getSecondaryAttribute,
  getVariantSchema,
  legacyVariantToSelections,
  normalizeAttributeValues,
  resolvePreferredCategoryType,
  selectionsToLegacyVariant,
  type CategoryAttributeDefinition,
  type VariantAttributeSelections,
  type VariantSchema,
} from '../../lib/variantSchema';

type CatNode = CategoryDto & { children: CatNode[] };

function buildCategoryTree(cats: CategoryDto[]): CatNode[] {
  const map = new Map<string, CatNode>();
  cats.forEach(c => map.set(c.id, { ...c, children: [] }));
  const roots: CatNode[] = [];
  cats.forEach(c => {
    const node = map.get(c.id)!;
    if (c.parentId && map.has(c.parentId)) {
      map.get(c.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });
  const sort = (nodes: CatNode[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder);
    nodes.forEach(n => sort(n.children));
    return nodes;
  };
  return sort(roots);
}

function withAncestors(ids: string[], allCats: CategoryDto[]): string[] {
  const catMap = new Map(allCats.map(c => [c.id, c]));
  const result = new Set(ids);
  for (const id of ids) {
    let cat = catMap.get(id);
    while (cat?.parentId) {
      result.add(cat.parentId);
      cat = catMap.get(cat.parentId);
    }
  }
  return Array.from(result);
}

function collectSelectedDescendantCount(node: CatNode, selected: string[]): number {
  let count = 0;
  for (const child of node.children) {
    if (selected.includes(child.id)) count += 1;
    count += collectSelectedDescendantCount(child, selected);
  }
  return count;
}

function CategoryTreeItem({
  node,
  depth,
  selected,
  onToggle,
  expanded,
  onToggleExpand,
}: {
  node: CatNode;
  depth: number;
  selected: string[];
  onToggle: (id: string) => void;
  expanded: Set<string>;
  onToggleExpand: (id: string) => void;
}) {
  const hasChildren = node.children.length > 0;
  const isOpen = expanded.has(node.id);
  const isSelected = selected.includes(node.id);
  const descendantsSelected = hasChildren ? collectSelectedDescendantCount(node, selected) : 0;

  // Visual hierarchy by depth — stronger contrast, clearer rhythm
  const rowClass =
    depth === 0
      ? 'text-[0.82rem] font-semibold text-[#1A1A1A] dark:text-[#E8DCC8] tracking-tight'
      : depth === 1
        ? 'text-[0.78rem] font-medium text-[#2A2A2A] dark:text-[#D6C8B5]'
        : depth === 2
          ? 'text-[0.74rem] text-[#4A4A4A] dark:text-[#C2B49E]'
          : 'text-[0.7rem] text-[#6A6A6A] dark:text-[#A89C88]';

  const indent = depth * 16;

  return (
    <div>
      <div
        className={[
          'flex items-center gap-1.5 py-1 pr-2 group transition-colors rounded-sm',
          isSelected
            ? 'bg-[#B76E79]/8 dark:bg-[#E4B8BF]/12'
            : 'hover:bg-[#B76E79]/5 dark:hover:bg-[#E4B8BF]/8',
        ].join(' ')}
        style={{ paddingLeft: `${indent + 4}px` }}
      >
        {/* Chevron toggle for parents, spacer for leaves */}
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggleExpand(node.id)}
            className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-charcoal/45 dark:text-[#D6C8B5]/55 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
            aria-label={isOpen ? 'Contraer' : 'Expandir'}
          >
            {isOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
        ) : (
          <span className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-charcoal/25 dark:text-[#D6C8B5]/30">
            <Tag size={9} />
          </span>
        )}

        <label className="flex items-center gap-2 cursor-pointer flex-1 min-w-0">
          <input
            type="checkbox"
            className="w-3.5 h-3.5 shrink-0 accent-[#B76E79]"
            checked={isSelected}
            onChange={() => onToggle(node.id)}
          />
          {hasChildren && (
            <span className="shrink-0 text-pe-charcoal/45 dark:text-[#D6C8B5]/55 group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors">
              {isOpen ? <FolderOpen size={12} /> : <Folder size={12} />}
            </span>
          )}
          <span className={`font-sans leading-snug truncate group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors ${rowClass}`}>
            {node.nameEs}
          </span>
          {hasChildren && (
            <span className="shrink-0 ml-auto font-sans text-[0.6rem] text-pe-charcoal/40 dark:text-[#D6C8B5]/45">
              {descendantsSelected > 0
                ? `${descendantsSelected}/${node.children.length}`
                : node.children.length}
            </span>
          )}
        </label>
      </div>

      {hasChildren && isOpen && (
        <div>
          {node.children.map(child => (
            <CategoryTreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              selected={selected}
              onToggle={onToggle}
              expanded={expanded}
              onToggleExpand={onToggleExpand}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface Props {
  product?: ProductDto | null;
  onSave: (saved: ProductDto) => void;
  onCancel: () => void;
  token?: string;
}

const EMPTY_FORM = {
  name: '',
  description: '',
  amount: '',
  listAmount: '',
  currency: 'CLP',
  imageUrl: '',
  condition: 'NEW' as 'NEW' | 'USED',
  brand: '',
  stock: '1',
  active: true,
};

type VariantRow = {
  attributes: VariantAttributeSelections;
  stock: string;
};

type FlatVariantRow = {
  color: ProductVariantDto['color'];
  size: ProductVariantDto['size'];
  stock: string;
};

const DEFAULT_TRANSFORM_PROMPT = 'Generar una imagen de tamano ideal para Instagram (la presenta una modelo en un fondo de boutique de lujo), para campana de invierno. Fijate bien en el diseno y color; mantener tambien textura y corte. Sin texto, sin logos, sin marcas de agua.';
const DEFAULT_INFERRED_BRAND = 'Pilar Estilo';
const DEFAULT_INFERRED_CONDITION: 'NEW' | 'USED' = 'USED';
const DEFAULT_INFERRED_BASE_PRICE = 24990;
const DEFAULT_INFERRED_LIST_MULTIPLIER = 1.35;

function roundPriceToThousand(value: number): number {
  return Math.max(1000, Math.round(value / 1000) * 1000);
}

function inferSuggestedPriceFromCopy(title: string, description: string, basePrice: number): number {
  const text = `${title} ${description}`.toLowerCase();
  if (text.includes('abrigo') || text.includes('parka') || text.includes('chaqueta')) return roundPriceToThousand(basePrice * 1.4);
  if (text.includes('blazer')) return roundPriceToThousand(basePrice * 1.16);
  if (text.includes('vestido')) return roundPriceToThousand(basePrice * 1.12);
  if (text.includes('falda') || text.includes('pantalon') || text.includes('jeans')) return roundPriceToThousand(basePrice * 0.9);
  if (text.includes('blusa') || text.includes('camisa') || text.includes('top')) return roundPriceToThousand(basePrice * 0.8);
  if (text.includes('poleron') || text.includes('sweater') || text.includes('chaleco')) return roundPriceToThousand(basePrice * 0.96);
  if (text.includes('premium') || text.includes('lujo') || text.includes('vintage')) return roundPriceToThousand(basePrice * 1.3);
  return roundPriceToThousand(basePrice);
}

function inferSuggestedListPrice(basePrice: number, multiplier: number): number {
  return roundPriceToThousand(basePrice * multiplier);
}

function normalizeVariantRows(rows: VariantRow[], schema: VariantSchema): ProductVariantDto[] {
  return rows.map((row) => {
    const stock = parseSafeStock(row.stock);
    return selectionsToLegacyVariant(row.attributes, stock, schema);
  });
}

function parseSafeStock(value: string | number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return 0;
  return Math.floor(parsed);
}

function toVariantRows(rows: FlatVariantRow[], schema: VariantSchema): VariantRow[] {
  return rows.map((row) => ({
    attributes: legacyVariantToSelections(
      {
        color: row.color,
        size: row.size,
        stock: parseSafeStock(row.stock),
        stockOnHand: parseSafeStock(row.stock),
        stockReserved: 0,
        stockAvailable: parseSafeStock(row.stock),
      },
      schema,
    ),
    stock: String(parseSafeStock(row.stock)),
  }));
}

function createVariantRow(schema: VariantSchema, stock = '0'): VariantRow {
  return {
    attributes: createEmptyVariantSelections(schema),
    stock,
  };
}

function rebindVariantRowsToSchema(rows: VariantRow[], fromSchema: VariantSchema, toSchema: VariantSchema): VariantRow[] {
  return rows.map((row) => ({
    attributes: legacyVariantToSelections(
      selectionsToLegacyVariant(row.attributes, parseSafeStock(row.stock), fromSchema),
      toSchema,
    ),
    stock: String(parseSafeStock(row.stock)),
  }));
}

function reconcileRowsWithRealStock(rows: FlatVariantRow[], realStock: number): FlatVariantRow[] {
  const target = Math.max(0, Math.floor(realStock));
  const normalized = (rows.length ? rows : [{ color: 'Base', size: 'UNICO', stock: '0' }]).map((row) => ({
    color: row.color.trim() || 'Base',
    size: row.size || 'UNICO',
    stock: String(parseSafeStock(row.stock)),
  }));

  const working = normalized.map((row) => ({ ...row, stockValue: parseSafeStock(row.stock) }));
  let currentTotal = working.reduce((sum, row) => sum + row.stockValue, 0);

  if (currentTotal < target) {
    working[0].stockValue += target - currentTotal;
  } else if (currentTotal > target) {
    let remainingToDiscount = currentTotal - target;
    for (let index = working.length - 1; index >= 0 && remainingToDiscount > 0; index -= 1) {
      const discount = Math.min(working[index].stockValue, remainingToDiscount);
      working[index].stockValue -= discount;
      remainingToDiscount -= discount;
    }
  }

  return working.map((row) => ({
    color: row.color,
    size: row.size,
    stock: String(row.stockValue),
  }));
}

export default function ProductForm({ product, onSave, onCancel, token }: Props) {
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [aiToolsOpen, setAiToolsOpen] = useState(false);
  const [lastUploadedFile, setLastUploadedFile] = useState<File | null>(null);
  const [aiRunning, setAiRunning] = useState(false);
  const [aiTransformRunning, setAiTransformRunning] = useState(false);
  const [aiInfo, setAiInfo] = useState('');
  const [aiTransformPrompt, setAiTransformPrompt] = useState(DEFAULT_TRANSFORM_PROMPT);
  const [aiTransformPreviewUrl, setAiTransformPreviewUrl] = useState('');
  const [aiInferDefaults, setAiInferDefaults] = useState<{
    brand: string;
    condition: 'NEW' | 'USED';
    basePrice: number;
    listMultiplier: number;
  }>({
    brand: DEFAULT_INFERRED_BRAND,
    condition: DEFAULT_INFERRED_CONDITION,
    basePrice: DEFAULT_INFERRED_BASE_PRICE,
    listMultiplier: DEFAULT_INFERRED_LIST_MULTIPLIER,
  });
  const [variantRows, setVariantRows] = useState<VariantRow[]>([]);
  const [stockSyncHint, setStockSyncHint] = useState('');
  const [selectedCatIds, setSelectedCatIds] = useState<string[]>([]);
  const [initialSnapshot, setInitialSnapshot] = useState('');
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [expandedCatIds, setExpandedCatIds] = useState<Set<string>>(new Set());
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [apiError, setApiError] = useState('');
  const [heroAssigningSlot, setHeroAssigningSlot] = useState<'left' | 'right' | null>(null);
  const [heroAssignFeedback, setHeroAssignFeedback] = useState('');
  const [unsavedConfirmOpen, setUnsavedConfirmOpen] = useState(false);
  const resolvedCategoryType = useMemo(
    () => resolvePreferredCategoryType({
      categoryTypes: selectedCatIds.length === 0 ? product?.categoryTypes : undefined,
      categoryIds: selectedCatIds,
      categories,
    }),
    [product?.categoryTypes, selectedCatIds, categories],
  );
  const variantSchema = useMemo(() => getVariantSchema(resolvedCategoryType), [resolvedCategoryType]);
  const primaryAttribute = useMemo(() => getPrimaryAttribute(variantSchema), [variantSchema]);
  const secondaryAttribute = useMemo(() => getSecondaryAttribute(variantSchema), [variantSchema]);
  const previousSchemaRef = useRef<VariantSchema>(variantSchema);
  const catInitKeyRef = useRef('');

  useEffect(() => {
    getCategories()
      .then(cats => {
        setCategories(cats);
        // Auto-expand all parent categories so user sees the full structure on first open
        const parentIds = new Set(cats.filter(c => c.parentId).map(c => c.parentId as string));
        setExpandedCatIds(parentIds);
      })
      .catch(() => {});
  }, []);

  function toggleCategoryExpanded(id: string) {
    setExpandedCatIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function expandAllCategories() {
    const parentIds = new Set(categories.filter(c => c.parentId).map(c => c.parentId as string));
    setExpandedCatIds(parentIds);
  }

  function collapseAllCategories() {
    setExpandedCatIds(new Set());
  }

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getSystemSettings(token)
      .then((settings) => {
        if (cancelled) return;
        const condition = settings.productAiInferDefaultCondition === 'NEW' ? 'NEW' : 'USED';
        const basePrice =
          typeof settings.productAiInferBasePrice === 'number' && settings.productAiInferBasePrice >= 1000
            ? settings.productAiInferBasePrice
            : DEFAULT_INFERRED_BASE_PRICE;
        const listMultiplier =
          typeof settings.productAiInferListPriceMultiplier === 'number' &&
          settings.productAiInferListPriceMultiplier >= 1 &&
          settings.productAiInferListPriceMultiplier <= 5
            ? settings.productAiInferListPriceMultiplier
            : DEFAULT_INFERRED_LIST_MULTIPLIER;
        setAiInferDefaults({
          brand: settings.productAiInferDefaultBrand?.trim() || DEFAULT_INFERRED_BRAND,
          condition,
          basePrice,
          listMultiplier,
        });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    const previousSchema = previousSchemaRef.current;
    if (previousSchema.key === variantSchema.key) {
      return;
    }
    setVariantRows((prev) => rebindVariantRowsToSchema(prev, previousSchema, variantSchema));
    previousSchemaRef.current = variantSchema;
  }, [variantSchema]);

  // Clear selected categories only when the product identity changes (open/close form),
  // NOT on variantSchema changes triggered by category toggles.
  useEffect(() => {
    setSelectedCatIds([]);
    catInitKeyRef.current = '';
  }, [product]);

  useEffect(() => {
    if (product) {
      const existingFlatVariants: FlatVariantRow[] = (product.variants ?? []).map((variant) => ({
        color: variant.color,
        size: variant.size,
        stock: String(variant.stock),
      }));
      const seededRows = existingFlatVariants.length > 0
        ? existingFlatVariants
        : [{ color: 'Base', size: 'UNICO', stock: String(product.stock) }];
      const reconciledRows = reconcileRowsWithRealStock(seededRows, product.stock);
      const incomingTotal = existingFlatVariants.reduce((sum, row) => sum + parseSafeStock(row.stock), 0);
      const nextForm = {
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        listAmount: product.listPrice?.amount != null ? String(product.listPrice.amount) : '',
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      };
      const nextRows = toVariantRows(reconciledRows, variantSchema);
      setForm(nextForm);
      setVariantRows(nextRows);
      previousSchemaRef.current = variantSchema;
      setStockSyncHint(
        incomingTotal !== product.stock
          ? `Stock sincronizado con disponibilidad real. Revisa ${primaryAttribute.label.toLowerCase()}, ${secondaryAttribute.label.toLowerCase()} y stock antes de guardar.`
          : ''
      );
      // Categories are initialized by the separate categories useEffect.
      // Do not reset them here — doing so would undo interactive toggles when variantSchema changes.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], variantSchema));
    } else {
      const nextForm = { ...EMPTY_FORM };
      const nextRows = [createVariantRow(variantSchema, EMPTY_FORM.stock)];
      setForm(nextForm);
      setVariantRows(nextRows);
      previousSchemaRef.current = variantSchema;
      setStockSyncHint('');
      // Categories for new products are cleared by the dedicated product-change effect below.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], variantSchema));
    }
    setErrors({});
    setApiError('');
    setAiToolsOpen(false);
    setLastUploadedFile(null);
    setAiInfo('');
    setAiRunning(false);
    setAiTransformRunning(false);
    setAiTransformPrompt(DEFAULT_TRANSFORM_PROMPT);
    setAiTransformPreviewUrl('');
    setHeroAssignFeedback('');
    setHeroAssigningSlot(null);
    setUnsavedConfirmOpen(false);
  }, [product, variantSchema, primaryAttribute.label, secondaryAttribute.label]);

  useEffect(() => {
    // Guard: only reinitialize when the product or the categories list changes,
    // not when variantSchema changes (which happens on every category toggle).
    const key = `${product?.id ?? '__new__'}:${categories.length}`;
    if (!product?.categorySlugs || categories.length === 0 || catInitKeyRef.current === key) return;
    catInitKeyRef.current = key;
    {
      const ids = categories.filter((c) => product.categorySlugs!.includes(c.slug)).map((c) => c.id);
      const existingFlatVariants: FlatVariantRow[] = (product.variants ?? []).map((variant) => ({
        color: variant.color,
        size: variant.size,
        stock: String(variant.stock),
      }));
      const seededRows = existingFlatVariants.length > 0
        ? existingFlatVariants
        : [{ color: 'Base', size: 'UNICO', stock: String(product.stock) }];
      const snapshotRows = toVariantRows(reconcileRowsWithRealStock(seededRows, product.stock), variantSchema);
      const snapshotForm = {
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        listAmount: product.listPrice?.amount != null ? String(product.listPrice.amount) : '',
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      };
      const fixedIds = withAncestors(ids, categories);
      setSelectedCatIds(fixedIds);
      // Keep initialSnapshot with original ids so form is dirty when parents were auto-added,
      // forcing the user to save and persist the corrected category selection.
      setInitialSnapshot(makeSnapshot(snapshotForm, snapshotRows, ids, variantSchema));
    }
  }, [categories, product, variantSchema]);  // variantSchema kept in deps for makeSnapshot freshness; catInitKeyRef guards re-init

  function toggleCategory(id: string) {
    setSelectedCatIds((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      return withAncestors([...prev, id], categories);
    });
  }

  function addVariantRow() {
    setVariantRows((prev) => [...prev, createVariantRow(variantSchema)]);
  }

  function updateVariantRow(index: number, patch: Partial<VariantRow>) {
    setVariantRows((prev) =>
      prev.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row))
    );
  }

  function updateVariantAttributeValue(index: number, attribute: CategoryAttributeDefinition, rawValue: string) {
    setVariantRows((prev) =>
      prev.map((row, rowIndex) => {
        if (rowIndex !== index) return row;
        const nextValues = normalizeAttributeValues(attribute, [rawValue]);
        return {
          ...row,
          attributes: {
            ...row.attributes,
            [attribute.code]: nextValues,
          },
        };
      })
    );
  }

  function toggleVariantAttributeOption(index: number, attribute: CategoryAttributeDefinition, optionValue: string) {
    setVariantRows((prev) =>
      prev.map((row, rowIndex) => {
        if (rowIndex !== index) return row;
        const currentValues = getAttributeValues(row.attributes, attribute);
        const alreadySelected = currentValues.includes(optionValue);
        const nextRawValues = attribute.allowMultiple
          ? (alreadySelected
            ? currentValues.filter((value) => value !== optionValue)
            : [...currentValues.filter((value) => value !== 'UNICO'), optionValue])
          : (alreadySelected ? [] : [optionValue]);
        return {
          ...row,
          attributes: {
            ...row.attributes,
            [attribute.code]: normalizeAttributeValues(attribute, nextRawValues),
          },
        };
      })
    );
  }

  function removeVariantRow(index: number) {
    setVariantRows((prev) => prev.filter((_, rowIndex) => rowIndex !== index));
  }

  const variantTotalStock = variantRows.reduce((sum, row) => sum + parseSafeStock(row.stock), 0);

  function validate(): boolean {
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = 'Nombre requerido';
    if (!form.brand.trim()) e.brand = 'Marca requerida';
    if (!form.description.trim()) e.description = 'Descripcion requerida';
    if (!form.amount || isNaN(Number(form.amount)) || Number(form.amount) <= 0) e.amount = 'Precio valido requerido';
    if (form.listAmount.trim()) {
      if (isNaN(Number(form.listAmount)) || Number(form.listAmount) <= 0) {
        e.listAmount = 'Precio lista valido requerido';
      } else if (!isNaN(Number(form.amount)) && Number(form.listAmount) <= Number(form.amount)) {
        e.listAmount = 'El precio lista debe ser mayor al precio oferta';
      }
    }
    if (variantRows.length === 0) {
      e.combinations = 'Debes agregar al menos una combinacion';
    } else {
      const seen = new Set<string>();
      for (let idx = 0; idx < variantRows.length; idx += 1) {
        const row = variantRows[idx];
        for (const attribute of variantSchema.attributes) {
          const values = getAttributeValues(row.attributes, attribute);
          const normalizedValues = normalizeAttributeValues(attribute, values);
          if (attribute.required && normalizedValues.length === 0) {
            e.combinations = `${attribute.label} requerido en fila ${idx + 1}`;
            break;
          }
          if (normalizedValues.length > 0 && values.join('|') !== normalizedValues.join('|')) {
            e.combinations = `${attribute.label} invalido en fila ${idx + 1}`;
            break;
          }
        }
        if (e.combinations) break;
        if (!row.stock || isNaN(Number(row.stock)) || Number(row.stock) < 0) {
          e.combinations = `Stock valido requerido en fila ${idx + 1}`;
          break;
        }
        const normalizedVariant = selectionsToLegacyVariant(row.attributes, parseSafeStock(row.stock), variantSchema);
        const key = `${normalizedVariant.color.trim().toLowerCase()}::${normalizedVariant.size}`;
        if (seen.has(key)) {
          e.combinations = `No se permiten combinaciones duplicadas (${primaryAttribute.label.toLowerCase()} + ${secondaryAttribute.label.toLowerCase()})`;
          break;
        }
        seen.add(key);
      }
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function makeSnapshot(nextForm: typeof form, nextRows: VariantRow[], nextCatIds: string[], schema: VariantSchema): string {
    const variants = normalizeVariantRows(nextRows, schema)
      .map((variant) => ({
        color: variant.color.trim().toLowerCase(),
        size: variant.size,
        stock: Number(variant.stock),
      }))
      .sort((a, b) => `${a.color}::${a.size}`.localeCompare(`${b.color}::${b.size}`));
    const cats = [...nextCatIds].sort();
    return JSON.stringify({
      name: nextForm.name.trim(),
      description: nextForm.description.trim(),
      amount: nextForm.amount.trim(),
      listAmount: nextForm.listAmount.trim(),
      imageUrl: nextForm.imageUrl.trim(),
      condition: nextForm.condition,
      brand: nextForm.brand.trim(),
      active: nextForm.active,
      categories: cats,
      variants,
    });
  }

  const currentSnapshot = makeSnapshot(form, variantRows, selectedCatIds, variantSchema);
  const isDirty = initialSnapshot.length > 0 && currentSnapshot !== initialSnapshot;
  function handleAttemptClose() {
    if (!isDirty) {
      onCancel();
      return;
    }
    setUnsavedConfirmOpen(true);
  }

  function handleDiscardAndClose() {
    setUnsavedConfirmOpen(false);
    onCancel();
  }

  useEffect(() => {
    if (!unsavedConfirmOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setUnsavedConfirmOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [unsavedConfirmOpen]);
  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }

    setSaving(true);
    setApiError('');

    try {
      const normalizedVariants = normalizeVariantRows(variantRows, variantSchema);
      const payload: CreateProductRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: { amount: Number(form.amount), currency: form.currency },
        ...(form.listAmount.trim()
          ? { listPrice: { amount: Number(form.listAmount), currency: form.currency } }
          : {}),
        imageUrl: form.imageUrl.trim() || '/api/media/products/product-001.jpg',
        condition: form.condition,
        brand: form.brand.trim(),
        stock: variantTotalStock,
        active: form.active,
        categoryIds: selectedCatIds,
        variants: normalizedVariants,
      };

      let saved: ProductDto;
      if (product) {
        saved = await updateProduct(product.id, { ...payload, active: form.active }, token);
      } else {
        saved = await createProduct(payload, token);
      }

      onSave(saved);
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Error al guardar');
    } finally {
      setSaving(false);
    }
  }

  async function resolveImageFileForAi(): Promise<File> {
    if (lastUploadedFile) {
      return lastUploadedFile;
    }
    const currentUrl = form.imageUrl.trim();
    if (!currentUrl) {
      throw new Error('Primero sube una imagen para usar el flujo IA');
    }
    const response = await fetch(currentUrl);
    if (!response.ok) {
      throw new Error('No se pudo leer la imagen actual para procesarla con IA');
    }
    const blob = await response.blob();
    const ext = blob.type.includes('png') ? 'png' : blob.type.includes('webp') ? 'webp' : 'jpg';
    return new File([blob], `producto-ia.${ext}`, { type: blob.type || 'image/jpeg' });
  }

  async function handleInferWithAi() {
    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    setApiError('');
    setAiInfo('');
    setAiRunning(true);
    try {
      const file = await resolveImageFileForAi();
      const inference = await inferSingleProductAi(token, file, form.brand.trim() || aiInferDefaults.brand);
      const suggestedBasePrice = inferSuggestedPriceFromCopy(
        inference.title ?? '',
        inference.description ?? '',
        aiInferDefaults.basePrice
      );
      setForm((prev) => {
        const shouldApplyDefaultCondition = !product && prev.condition === DEFAULT_INFERRED_CONDITION;
        return {
          ...prev,
          name: inference.title?.trim() || prev.name,
          description: inference.description?.trim() || prev.description,
          brand: prev.brand.trim().length > 0 ? prev.brand : aiInferDefaults.brand,
          condition: shouldApplyDefaultCondition ? aiInferDefaults.condition : prev.condition,
          amount:
            prev.amount.trim().length > 0
              ? prev.amount
              : String(suggestedBasePrice),
          listAmount:
            prev.listAmount.trim().length > 0
              ? prev.listAmount
              : String(inferSuggestedListPrice(suggestedBasePrice, aiInferDefaults.listMultiplier)),
        };
      });
      setAiInfo('IA completada (OpenAI). Texto sugerido aplicado; revisa y guarda manualmente.');
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'No se pudo procesar la imagen con IA');
    } finally {
      setAiRunning(false);
    }
  }

  async function handleTransformWithAi() {
    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    setApiError('');
    setAiInfo('');
    setAiTransformRunning(true);
    try {
      const file = await resolveImageFileForAi();
      const transformed = await transformSingleProductAiImage(token, file, {
        prompt: aiTransformPrompt,
        brandHint: form.brand.trim() || undefined,
      });
      const preview = transformed.processedWebUrl || transformed.processedMasterUrl;
      setAiTransformPreviewUrl(preview);
      setAiInfo(`Transformacion completada (${transformed.provider}). Revisa preview y confirma si quieres reemplazar la imagen actual.`);
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'No se pudo transformar la imagen con IA');
    } finally {
      setAiTransformRunning(false);
    }
  }

  function handleApplyTransformPreview() {
    if (!aiTransformPreviewUrl) {
      return;
    }
    setForm((prev) => ({ ...prev, imageUrl: aiTransformPreviewUrl }));
    setLastUploadedFile(null);
    setAiInfo('Imagen transformada aplicada al formulario. Puedes guardar o ajustar antes de guardar.');
  }

  async function handleAssignHeroFromCurrentProduct(slot: 'left' | 'right') {
    if (!product?.id) return;
    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }
    setHeroAssigningSlot(slot);
    setHeroAssignFeedback('');
    try {
      await assignHeroModelFromProduct(slot, product.id, token);
      setHeroAssignFeedback(
        `Modelo ${slot === 'left' ? 'izquierdo' : 'derecho'} del hero actualizado desde este producto.`,
      );
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'No se pudo asignar la imagen del producto al hero');
    } finally {
      setHeroAssigningSlot(null);
    }
  }

  const inputClass =
    'w-full font-sans text-[0.82rem] border border-pe-black/30 dark:border-[#3F2A2F] px-2.5 py-1.5 bg-[#fffdfa] dark:bg-[#1F1518] text-[#1A1A1A] dark:text-[#E8DCC8] placeholder-pe-charcoal/40 dark:placeholder-[#D6C8B5]/35 focus:outline-none focus:border-pe-rose focus:ring-1 focus:ring-pe-rose/25 transition-colors';
  const labelClass = 'block font-sans text-[0.68rem] tracking-[0.14em] uppercase text-[#1A1A1A]/70 dark:text-[#D6C8B5]/65 mb-1';
  const errorClass = 'font-sans text-xs text-red-500 dark:text-red-300 mt-1';

  const categoryTree = buildCategoryTree(categories);

  return (
    <div
      className="fixed inset-0 bg-[#1A1A1A]/68 dark:bg-black/72 z-50 flex items-center justify-center p-2 sm:p-4"
      role="dialog"
      aria-modal="true"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          handleAttemptClose();
        }
      }}
    >
      <div
        className="bg-[#F8F4EF] dark:bg-[#181214] w-full max-w-xl max-h-[92vh] overflow-y-auto p-3 sm:p-5 shadow-2xl border border-pe-black/20 dark:border-[#3F2A2F]"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-['Cormorant_Garamond',serif] text-[#1A1A1A] dark:text-[#E8DCC8] text-xl font-light">
            {product ? 'Editar Producto' : 'Nuevo Producto'}
          </h2>
          <button
            onClick={handleAttemptClose}
            className="inline-flex items-center justify-center w-8 h-8 text-[#3A3A3A]/40 dark:text-[#D6C8B5]/45 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
            aria-label="Cerrar formulario"
          >
            <X size={16} />
          </button>
        </div>

        {apiError && <div className="bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800/50 text-red-700 dark:text-red-300 text-sm px-4 py-2 mb-4">{apiError}</div>}
        {aiInfo && <div className="bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800/50 text-emerald-700 dark:text-emerald-300 text-sm px-4 py-2 mb-4">{aiInfo}</div>}
        {heroAssignFeedback && <div className="bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800/50 text-emerald-700 dark:text-emerald-300 text-sm px-4 py-2 mb-4">{heroAssignFeedback}</div>}

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
          <div>
            <label htmlFor="pf-name" className={labelClass}>
              Nombre
            </label>
            <input
              id="pf-name"
              type="text"
              className={inputClass}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            {errors.name && <p className={errorClass}>{errors.name}</p>}
          </div>

          <div>
            <label htmlFor="pf-brand" className={labelClass}>
              Marca
            </label>
            <input
              id="pf-brand"
              type="text"
              className={inputClass}
              value={form.brand}
              onChange={(e) => setForm({ ...form, brand: e.target.value })}
              required
            />
            {errors.brand && <p className={errorClass}>{errors.brand}</p>}
          </div>

          <div>
            <label htmlFor="pf-desc" className={labelClass}>
              Descripcion
            </label>
            <textarea
              id="pf-desc"
              className={inputClass + ' resize-none h-16'}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              required
            />
            {errors.description && <p className={errorClass}>{errors.description}</p>}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            <div className="col-span-1">
              <label htmlFor="pf-price" className={labelClass}>
                Precio (CLP)
              </label>
              <input
                id="pf-price"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
                required
              />
              {errors.amount && <p className={errorClass}>{errors.amount}</p>}
            </div>
            <div className="col-span-1">
              <label htmlFor="pf-list-price" className={labelClass + ' line-through decoration-1'}>
                Precio
              </label>
              <input
                id="pf-list-price"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={form.listAmount}
                onChange={(e) => setForm({ ...form, listAmount: e.target.value })}
                placeholder="Opcional"
              />
              {errors.listAmount && <p className={errorClass}>{errors.listAmount}</p>}
            </div>
            <div className="col-span-2 sm:col-span-2 lg:col-span-1">
              <label className={labelClass}>
                Condicion
              </label>
              <div className="grid grid-cols-2 border border-pe-black/30 dark:border-[#3F2A2F] bg-[#fffdfa] dark:bg-[#1F1518]">
                {[
                  { value: 'NEW' as const, label: 'Nuevo' },
                  { value: 'USED' as const, label: 'Usado' },
                ].map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setForm({ ...form, condition: option.value })}
                    className={[
                      'px-3 py-2 font-sans text-[0.72rem] uppercase tracking-[0.1em] transition-colors',
                      form.condition === option.value
                        ? 'bg-[#B76E79] text-white'
                        : 'text-pe-charcoal/70 dark:text-[#D6C8B5]/65 hover:bg-[#B76E79]/10',
                    ].join(' ')}
                    aria-pressed={form.condition === option.value}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <div>
              <label htmlFor="pf-stock-total" className={labelClass}>
                Stock total
              </label>
              <input
                id="pf-stock-total"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={String(variantTotalStock)}
                readOnly
                disabled
              />
              <p className="font-sans text-[0.66rem] text-pe-charcoal/55 dark:text-[#D6C8B5]/55 mt-1">
                Se calcula automaticamente desde {primaryAttribute.label.toLowerCase()} + {secondaryAttribute.label.toLowerCase()} + stock por variante.
              </p>
            </div>
            <div className="flex flex-col justify-end pb-1 gap-2">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-[#B76E79]"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                <span className="font-sans text-sm text-[#1A1A1A] dark:text-[#E8DCC8] select-none">Activo</span>
              </div>
            </div>
          </div>

          <div className="border border-pe-black/12 dark:border-[#3F2A2F] bg-pe-white dark:bg-[#1F1518] p-3 space-y-3">
            <div className="flex items-center justify-between">
              <p className={labelClass + ' mb-0'}>{variantSchema.title}</p>
              <button
                type="button"
                onClick={addVariantRow}
                className="inline-flex items-center gap-1.5 border border-[#B76E79]/40 text-[#8E4F58] dark:text-[#E4B8BF] font-sans text-[0.66rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:bg-[#B76E79]/10 transition-colors"
              >
                + Agregar
              </button>
            </div>

            <div className="space-y-2">
              {variantRows.map((row, index) => (
                <div key={index} className="grid grid-cols-1 sm:grid-cols-12 gap-2 items-start border border-pe-black/10 dark:border-[#3F2A2F] p-2">
                  <div className="sm:col-span-3 space-y-1">
                    <p className={labelClass + ' mb-0'}>{primaryAttribute.label}</p>
                    <input
                      type="text"
                      className={inputClass}
                      placeholder={primaryAttribute.placeholder ?? primaryAttribute.label}
                      value={getAttributeValue(row.attributes, primaryAttribute)}
                      onChange={(e) => updateVariantAttributeValue(index, primaryAttribute, e.target.value)}
                    />
                  </div>
                  <div className="sm:col-span-5 space-y-1">
                    <p className={labelClass + ' mb-0'}>{secondaryAttribute.label}{secondaryAttribute.allowMultiple ? '(s)' : ''}</p>
                    {secondaryAttribute.options.length > 0 && (
                      <div className="flex flex-wrap gap-1">
                        {secondaryAttribute.options.map((option) => {
                          const selected = getAttributeValues(row.attributes, secondaryAttribute).includes(option.value);
                          return (
                            <button
                              key={option.value}
                              type="button"
                              onClick={() => toggleVariantAttributeOption(index, secondaryAttribute, option.value)}
                              className={[
                                'px-2 py-1 font-sans text-[0.62rem] uppercase tracking-[0.08em] border transition-colors',
                                selected
                                  ? 'border-[#B76E79] bg-[#B76E79]/12 text-[#8E4F58] dark:text-[#E4B8BF]'
                                  : 'border-pe-black/20 dark:border-[#3F2A2F] text-pe-charcoal/65 dark:text-[#D6C8B5]/65 hover:border-[#B76E79]/40',
                              ].join(' ')}
                            >
                              {option.label}
                            </button>
                          );
                        })}
                      </div>
                    )}
                    {(secondaryAttribute.type === 'text' || secondaryAttribute.allowCustom) && (
                      <input
                        type="text"
                        className={inputClass}
                        placeholder={secondaryAttribute.placeholder ?? secondaryAttribute.label}
                        value={getAttributeValue(row.attributes, secondaryAttribute)}
                        onChange={(e) => updateVariantAttributeValue(index, secondaryAttribute, e.target.value)}
                      />
                    )}
                    <p className="font-sans text-[0.62rem] text-pe-charcoal/60 dark:text-[#D6C8B5]/60">
                      Valor guardado:{' '}
                      <span className="font-semibold">
                        {selectionsToLegacyVariant(row.attributes, parseSafeStock(row.stock), variantSchema).size || '-'}
                      </span>
                    </p>
                  </div>
                  <div className="sm:col-span-2 space-y-1">
                    <p className={labelClass + ' mb-0'}>Stock</p>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      className={inputClass}
                      value={row.stock}
                      onChange={(e) => updateVariantRow(index, { stock: e.target.value })}
                    />
                  </div>
                  <div className="sm:col-span-2 sm:pt-[1.22rem]">
                    <button
                      type="button"
                      onClick={() => removeVariantRow(index)}
                      className="w-full h-[36px] inline-flex items-center justify-center border border-red-300 dark:border-red-800/50 text-red-500 dark:text-red-300 text-[0.62rem] uppercase tracking-[0.1em] px-2 whitespace-nowrap hover:bg-red-50 dark:hover:bg-red-950/30 transition-colors"
                    >
                      Quitar
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <p className="font-sans text-[0.62rem] text-pe-charcoal/55 dark:text-[#D6C8B5]/55">
              {secondaryAttribute.allowMultiple
                ? `Puedes seleccionar varias ${secondaryAttribute.label.toLowerCase()}s por fila. Se guardan como valor compuesto.`
                : `La interfaz se adapta segun la metadata de categoria, sin asumir talla o color fijos.`}
            </p>

            {stockSyncHint && (
              <p className="font-sans text-[0.68rem] text-amber-700 dark:text-amber-300">
                {stockSyncHint}
              </p>
            )}
            {errors.combinations && <p className={errorClass}>{errors.combinations}</p>}
          </div>

          <div>
            <label className={labelClass}>Imagen del producto</label>
            <ImageDropzone
              folder="products"
              value={form.imageUrl.trim() || undefined}
              onUpload={url => {
                setForm(prev => ({ ...prev, imageUrl: url }));
                setAiTransformPreviewUrl('');
              }}
              onUploadedFile={(file) => {
                setLastUploadedFile(file);
                setAiTransformPreviewUrl('');
              }}
              token={token ?? ''}
            />
            {product?.id && (
              <div className="mt-2 grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => void handleAssignHeroFromCurrentProduct('left')}
                  disabled={heroAssigningSlot !== null}
                  className="inline-flex items-center justify-center border border-pe-black/20 dark:border-[#3F2A2F] text-pe-charcoal/70 dark:text-[#D6C8B5]/70 font-sans text-[0.62rem] uppercase tracking-[0.1em] py-1.5 hover:border-[#B76E79]/50 hover:text-[#8E4F58] dark:hover:text-[#E4B8BF] transition-colors disabled:opacity-50"
                >
                  {heroAssigningSlot === 'left' ? 'Asignando...' : 'Usar como Hero Izq'}
                </button>
                <button
                  type="button"
                  onClick={() => void handleAssignHeroFromCurrentProduct('right')}
                  disabled={heroAssigningSlot !== null}
                  className="inline-flex items-center justify-center border border-pe-black/20 dark:border-[#3F2A2F] text-pe-charcoal/70 dark:text-[#D6C8B5]/70 font-sans text-[0.62rem] uppercase tracking-[0.1em] py-1.5 hover:border-[#B76E79]/50 hover:text-[#8E4F58] dark:hover:text-[#E4B8BF] transition-colors disabled:opacity-50"
                >
                  {heroAssigningSlot === 'right' ? 'Asignando...' : 'Usar como Hero Der'}
                </button>
              </div>
            )}
          </div>

          <div className="border border-pe-black/12 dark:border-[#3F2A2F] bg-pe-white dark:bg-[#1F1518] p-3 space-y-2">
            <button
              type="button"
              onClick={() => setAiToolsOpen((prev) => !prev)}
              className="w-full flex items-center justify-between text-left"
              aria-expanded={aiToolsOpen}
            >
              <span className="font-sans text-sm text-[#1A1A1A] dark:text-[#E8DCC8]">Utilitarios IA</span>
              {aiToolsOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </button>
            {aiToolsOpen && (
              <div className="space-y-2">
                <button
                  type="button"
                  onClick={() => void handleInferWithAi()}
                  disabled={aiRunning}
                  className="inline-flex items-center gap-2 border border-[#B76E79]/35 text-[#8E4F58] dark:text-[#E4B8BF] font-sans text-[0.68rem] tracking-[0.1em] uppercase px-3 py-2 hover:bg-[#B76E79]/8 transition-colors disabled:opacity-50"
                >
                  {aiRunning ? <Loader2 size={13} className="animate-spin" /> : null}
                  {aiRunning ? 'Procesando IA...' : 'Inferir texto con IA'}
                </button>

                <div>
                  <label className={labelClass + ' mb-1'}>Prompt transformacion (opcional)</label>
                  <textarea
                    className={inputClass + ' resize-none h-20'}
                    value={aiTransformPrompt}
                    onChange={(e) => setAiTransformPrompt(e.target.value)}
                    placeholder={DEFAULT_TRANSFORM_PROMPT}
                  />
                </div>

                <button
                  type="button"
                  onClick={() => void handleTransformWithAi()}
                  disabled={aiTransformRunning}
                  className="inline-flex items-center gap-2 border border-[#B76E79]/35 text-[#8E4F58] dark:text-[#E4B8BF] font-sans text-[0.68rem] tracking-[0.1em] uppercase px-3 py-2 hover:bg-[#B76E79]/8 transition-colors disabled:opacity-50"
                >
                  {aiTransformRunning ? <Loader2 size={13} className="animate-spin" /> : null}
                  {aiTransformRunning ? 'Transformando...' : 'Transformar imagen con IA'}
                </button>

                {aiTransformPreviewUrl && (
                  <div className="border border-pe-black/12 dark:border-[#3F2A2F] bg-[#fffdfa] dark:bg-[#1A1012] p-2.5 space-y-2">
                    <p className="font-sans text-[0.68rem] tracking-[0.08em] uppercase text-pe-charcoal/65 dark:text-[#D6C8B5]/65">
                      Preview transformada
                    </p>
                    <img
                      src={aiTransformPreviewUrl}
                      alt="Previsualizacion transformada"
                      className="w-full max-w-[220px] h-auto border border-pe-black/15 dark:border-[#3F2A2F]"
                    />
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={handleApplyTransformPreview}
                        className="inline-flex items-center gap-2 border border-[#B76E79]/35 text-[#8E4F58] dark:text-[#E4B8BF] font-sans text-[0.64rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:bg-[#B76E79]/8 transition-colors"
                      >
                        Reemplazar imagen actual
                      </button>
                      <a
                        href={aiTransformPreviewUrl}
                        download
                        className="inline-flex items-center gap-2 border border-pe-black/20 dark:border-[#3F2A2F] text-pe-charcoal dark:text-[#D6C8B5] font-sans text-[0.64rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:border-[#B76E79] hover:text-[#B76E79] transition-colors"
                      >
                        Descargar preview
                      </a>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {categories.length > 0 && (
            <div>
              <div className="flex items-center justify-between mb-1.5 gap-2">
                <p className={labelClass + ' mb-0'}>Categorias</p>
                <div className="flex items-center gap-2">
                  {selectedCatIds.length > 0 && (
                    <span className="font-sans text-[0.65rem] text-[#B76E79] dark:text-[#E4B8BF]">
                      {selectedCatIds.length} {selectedCatIds.length === 1 ? 'seleccionada' : 'seleccionadas'}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={expandAllCategories}
                    className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-charcoal/50 dark:text-[#D6C8B5]/55 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
                  >
                    Expandir
                  </button>
                  <span className="font-sans text-[0.6rem] text-pe-charcoal/30 dark:text-[#D6C8B5]/25">|</span>
                  <button
                    type="button"
                    onClick={collapseAllCategories}
                    className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-charcoal/50 dark:text-[#D6C8B5]/55 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
                  >
                    Contraer
                  </button>
                </div>
              </div>
              <div className="border border-[#EDE3D8] dark:border-[#3F2A2F] bg-[#FDFAF6] dark:bg-[#1F1518] py-1.5 max-h-60 overflow-y-auto">
                {categoryTree.map(root => (
                  <CategoryTreeItem
                    key={root.id}
                    node={root}
                    depth={0}
                    selected={selectedCatIds}
                    onToggle={toggleCategory}
                    expanded={expandedCatIds}
                    onToggleExpand={toggleCategoryExpanded}
                  />
                ))}
              </div>
              <p className="font-sans text-[0.6rem] text-pe-charcoal/45 dark:text-[#D6C8B5]/45 mt-1">
                Al seleccionar una subcategoría, su categoría padre se marca automáticamente.
              </p>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-2.5 mt-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 inline-flex items-center justify-center gap-1.5 bg-[#B76E79] text-white font-sans text-xs tracking-widest uppercase py-2.5 hover:bg-[#8E4F58] transition-colors disabled:opacity-50"
            >
              {saving ? (
                <>
                  <Loader2 size={14} className="animate-spin" />
                  Guardando...
                </>
              ) : (
                <>
                  <Save size={14} />
                  {product ? 'Guardar Cambios' : 'Crear Producto'}
                </>
              )}
            </button>
            <button
              type="button"
              onClick={handleAttemptClose}
              className="flex-1 inline-flex items-center justify-center gap-1.5 border border-[#3A3A3A]/20 dark:border-[#3F2A2F] text-[#1A1A1A] dark:text-[#D6C8B5] font-sans text-xs tracking-widest uppercase py-2.5 hover:border-[#B76E79] hover:text-[#B76E79] dark:hover:border-[#E4B8BF] dark:hover:text-[#E4B8BF] transition-colors"
            >
              <X size={14} />
              Cancelar
            </button>
          </div>
        </form>
      </div>
      {unsavedConfirmOpen && (
        <div
          className="fixed inset-0 z-[70] bg-black/55 flex items-center justify-center p-4"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setUnsavedConfirmOpen(false);
            }
          }}
        >
          <div
            className="w-full max-w-sm border border-pe-black/20 dark:border-[#3F2A2F] bg-[#F8F4EF] dark:bg-[#181214] shadow-2xl p-4 sm:p-5"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <h3 className="font-['Cormorant_Garamond',serif] text-xl text-[#1A1A1A] dark:text-[#E8DCC8] mb-2">
              Salir sin guardar
            </h3>
            <p className="font-sans text-[0.82rem] text-pe-charcoal/75 dark:text-[#D6C8B5]/75 leading-relaxed">
              Tienes cambios sin guardar en este producto. Si sales ahora, los cambios se perderan.
            </p>
            <div className="mt-4 flex flex-col sm:flex-row gap-2">
              <button
                type="button"
                onClick={() => setUnsavedConfirmOpen(false)}
                className="flex-1 inline-flex items-center justify-center border border-[#3A3A3A]/20 dark:border-[#3F2A2F] text-[#1A1A1A] dark:text-[#D6C8B5] font-sans text-[0.68rem] tracking-[0.1em] uppercase py-2 hover:border-[#B76E79] hover:text-[#B76E79] dark:hover:border-[#E4B8BF] dark:hover:text-[#E4B8BF] transition-colors"
              >
                Seguir editando
              </button>
              <button
                type="button"
                onClick={handleDiscardAndClose}
                className="flex-1 inline-flex items-center justify-center border border-red-300 dark:border-red-800/50 text-red-600 dark:text-red-300 font-sans text-[0.68rem] tracking-[0.1em] uppercase py-2 hover:bg-red-50 dark:hover:bg-red-950/30 transition-colors"
              >
                Salir sin guardar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}


