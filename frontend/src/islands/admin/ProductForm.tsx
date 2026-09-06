import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { Loader2, Save, X, ChevronDown, ChevronRight, FolderOpen, Folder, Tag } from 'lucide-react';
import {
  createProduct,
  updateProduct,
  getCategories,
  getVariantTemplates,
  getSystemSettings,
  inferSingleProductAi,
  transformSingleProductAiImage,
  type ProductDto,
  type CreateProductRequest,
  type CategoryDto,
  type ProductVariantDto,
  type VariantTemplateDto,
} from '../../lib/api';
import ImageDropzone from './ImageDropzone';
import ProductGalleryEditor from './ProductGalleryEditor';
import {
  createEmptyVariantSelections,
  getAttributeValue,
  getAttributeValues,
  getPrimaryAttribute,
  getSecondaryAttribute,
  buildVariantSchema,
  legacyVariantToSelections,
  normalizeAttributeValues,
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
  readonly node: CatNode;
  readonly depth: number;
  readonly selected: string[];
  readonly onToggle: (id: string) => void;
  readonly expanded: Set<string>;
  readonly onToggleExpand: (id: string) => void;
}) {
  const hasChildren = node.children.length > 0;
  const isOpen = expanded.has(node.id);
  const isSelected = selected.includes(node.id);
  const descendantsSelected = hasChildren ? collectSelectedDescendantCount(node, selected) : 0;

  // Visual hierarchy by depth — stronger contrast, clearer rhythm
  const ROW_CLASS_BY_DEPTH = [
    'text-[0.82rem] font-semibold text-pe-black tracking-tight',
    'text-[0.78rem] font-medium text-pe-charcoal',
    'text-[0.74rem] text-pe-charcoal/75',
  ];
  const rowClass = ROW_CLASS_BY_DEPTH[depth] ?? 'text-[0.7rem] text-pe-charcoal/55';

  const indent = depth * 16;

  return (
    <div>
      <div
        className={[
          'flex items-center gap-1.5 py-1 pr-2 group transition-colors rounded-xs',
          isSelected
            ? 'bg-pe-rose/8'
            : 'hover:bg-pe-rose/5',
        ].join(' ')}
        style={{ paddingLeft: `${indent + 4}px` }}
      >
        {/* Chevron toggle for parents, spacer for leaves */}
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggleExpand(node.id)}
            className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted hover:text-pe-rose-ink transition-colors"
            aria-label={isOpen ? 'Contraer' : 'Expandir'}
          >
            {isOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
        ) : (
          <span className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted">
            <Tag size={9} />
          </span>
        )}

        <label className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer">
          <input
            type="checkbox"
            className="w-3.5 h-3.5 shrink-0 accent-pe-rose"
            checked={isSelected}
            onChange={() => onToggle(node.id)}
          />
          {hasChildren && (
            <span className="shrink-0 text-pe-muted group-hover:text-pe-rose-ink transition-colors">
              {isOpen ? <FolderOpen size={12} /> : <Folder size={12} />}
            </span>
          )}
          <span className={`font-sans leading-snug truncate group-hover:text-pe-rose-ink transition-colors ${rowClass}`}>
            {node.nameEs}
          </span>
          {hasChildren && (
            <span className="shrink-0 ml-auto font-sans text-[0.6rem] text-pe-muted">
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
  readonly product?: ProductDto | null;
  readonly onSave: (saved: ProductDto) => void;
  /** Announced so the panel can say it out loud; the inline message stays either way. */
  readonly onSaveFailed?: (message: string) => void;
  readonly onCancel: () => void;
  readonly token?: string;
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
  galleryImageUrls: [] as string[],
};

type VariantRow = {
  id: string;
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
    id: crypto.randomUUID(),
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
    id: crypto.randomUUID(),
    attributes: createEmptyVariantSelections(schema),
    stock,
  };
}

function rebindVariantRowsToSchema(rows: VariantRow[], fromSchema: VariantSchema, toSchema: VariantSchema): VariantRow[] {
  return rows.map((row) => ({
    id: row.id,
    attributes: legacyVariantToSelections(
      selectionsToLegacyVariant(row.attributes, parseSafeStock(row.stock), fromSchema),
      toSchema,
    ),
    stock: String(parseSafeStock(row.stock)),
  }));
}

function validateBasicFields(form: typeof EMPTY_FORM): Record<string, string> {
  const e: Record<string, string> = {};
  if (!form.name.trim()) e.name = 'Nombre requerido';
  if (!form.brand.trim()) e.brand = 'Marca requerida';
  if (!form.description.trim()) e.description = 'Descripcion requerida';
  if (!form.amount || Number.isNaN(Number(form.amount)) || Number(form.amount) <= 0) e.amount = 'Precio valido requerido';
  if (form.listAmount.trim()) {
    if (Number.isNaN(Number(form.listAmount)) || Number(form.listAmount) <= 0) {
      e.listAmount = 'Precio lista valido requerido';
    } else if (!Number.isNaN(Number(form.amount)) && Number(form.listAmount) <= Number(form.amount)) {
      e.listAmount = 'El precio lista debe ser mayor al precio oferta';
    }
  }
  return e;
}

function variantRowAttributeError(row: VariantRow, schema: VariantSchema, rowIndex: number): string | undefined {
  for (const attribute of schema.attributes) {
    const values = getAttributeValues(row.attributes, attribute);
    const normalizedValues = normalizeAttributeValues(attribute, values);
    if (attribute.required && normalizedValues.length === 0) {
      return `${attribute.label} requerido en fila ${rowIndex + 1}`;
    }
    // Only a genuinely unknown value in a strict options field is an error. Order, dedupe and case
    // are re-normalised by normalizeVariantRows on save, so the raw array not matching its
    // normalised form is not — comparing them byte-for-byte rejected valid rows whose stored
    // composite value (e.g. "S-M") was parsed under the generic fallback schema before the real
    // template loaded.
    if (attribute.type === 'choice' && !attribute.allowCustom) {
      const allowed = new Set(attribute.options.map((option) => option.value.toLowerCase()));
      if (normalizedValues.some((value) => !allowed.has(value.toLowerCase()))) {
        return `${attribute.label} invalido en fila ${rowIndex + 1}`;
      }
    }
  }
  return undefined;
}

function variantRowStockError(row: VariantRow, rowIndex: number): string | undefined {
  if (!row.stock || Number.isNaN(Number(row.stock)) || Number(row.stock) < 0) {
    return `Stock valido requerido en fila ${rowIndex + 1}`;
  }
  return undefined;
}

/**
 * One message for the whole variant table rather than per-row, matching how the rest of the
 * form reports errors -- the table is short enough that "fila N" pinpoints it well enough.
 */
function validateVariantRows(
  rows: VariantRow[],
  schema: VariantSchema,
  primaryAttribute: CategoryAttributeDefinition,
  secondaryAttribute: CategoryAttributeDefinition,
): string | undefined {
  if (rows.length === 0) return 'Debes agregar al menos una combinacion';

  const seen = new Set<string>();
  for (let idx = 0; idx < rows.length; idx += 1) {
    const row = rows[idx];
    const attributeError = variantRowAttributeError(row, schema, idx);
    if (attributeError) return attributeError;

    const stockError = variantRowStockError(row, idx);
    if (stockError) return stockError;

    const normalizedVariant = selectionsToLegacyVariant(row.attributes, parseSafeStock(row.stock), schema);
    const key = `${normalizedVariant.color.trim().toLowerCase()}::${normalizedVariant.size}`;
    if (seen.has(key)) {
      return `No se permiten combinaciones duplicadas (${primaryAttribute.label.toLowerCase()} + ${secondaryAttribute.label.toLowerCase()})`;
    }
    seen.add(key);
  }
  return undefined;
}

export interface ValidateProductFormArgs {
  readonly form: typeof EMPTY_FORM;
  readonly variantRows: VariantRow[];
  readonly variantSchema: VariantSchema;
  readonly primaryAttribute: CategoryAttributeDefinition;
  readonly secondaryAttribute: CategoryAttributeDefinition;
}

export function validateProductForm(args: ValidateProductFormArgs): Record<string, string> {
  const errors = validateBasicFields(args.form);

  const combinationsError = validateVariantRows(args.variantRows, args.variantSchema, args.primaryAttribute, args.secondaryAttribute);
  if (combinationsError) errors.combinations = combinationsError;

  return errors;
}

function compareIds(left: string, right: string): number {
  if (left === right) return 0;
  return left < right ? -1 : 1;
}

/**
 * A free-text variant value (a colour like "rojo y azul" or "verde, blanco"). The stored value is
 * trimmed and its whitespace collapsed, which ate the space or comma the moment you typed it — you
 * could never get past one word. The raw text is buffered locally while the field has focus so what
 * you type is what you see; the normalised value still flows up on every keystroke (so submitting
 * without blurring keeps the last edit). Same shape as OptionsInput in VariantFieldEditor.
 */
function VariantTextValueInput({
  value, placeholder, className, onCommit,
}: {
  readonly value: string;
  readonly placeholder: string;
  readonly className: string;
  readonly onCommit: (raw: string) => void;
}) {
  const [buffer, setBuffer] = useState<string | null>(null);
  return (
    <input
      type="text"
      className={className}
      placeholder={placeholder}
      value={buffer ?? value}
      onChange={(e) => {
        setBuffer(e.target.value);
        onCommit(e.target.value);
      }}
      onBlur={() => setBuffer(null)}
    />
  );
}

export default function ProductForm({ product, onSave, onSaveFailed, onCancel, token }: Props) {
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
  const [unsavedConfirmOpen, setUnsavedConfirmOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const confirmDialogRef = useRef<HTMLDialogElement | null>(null);

  // A ref callback rather than a mount-effect: fires the instant the node is actually attached,
  // regardless of which render that happens on. ProductForm itself is only ever rendered while
  // the parent wants it open.
  const setDialogRef = useCallback((node: HTMLDialogElement | null) => {
    dialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);
  const [variantTemplates, setVariantTemplates] = useState<VariantTemplateDto[]>([]);
  const [selectedVariantTemplateId, setSelectedVariantTemplateId] = useState<string | null>(null);
  const selectedTemplate = useMemo(
    () => variantTemplates.find((t) => t.id === selectedVariantTemplateId) ?? null,
    [variantTemplates, selectedVariantTemplateId],
  );
  const variantSchema = useMemo(
    () => buildVariantSchema(selectedTemplate?.config ?? null, selectedVariantTemplateId ?? 'GENERIC'),
    [selectedTemplate, selectedVariantTemplateId]
  );
  const primaryAttribute = useMemo(() => getPrimaryAttribute(variantSchema), [variantSchema]);
  const secondaryAttribute = useMemo(() => getSecondaryAttribute(variantSchema), [variantSchema]);
  const previousSchemaRef = useRef<VariantSchema>(variantSchema);
  /*
   * The schema the form is currently rendering, readable without depending on it.
   *
   * <p>The effect that seeds the form used to list variantSchema among its dependencies, so
   * choosing a variant type re-ran it: on a new product it restored EMPTY_FORM, wiping everything
   * typed and putting the selector back to "inherit"; on an existing one it restored the saved
   * type. Either way the choice could not stick, because making it undid it.
   */
  const currentSchemaRef = useRef<VariantSchema>(variantSchema);
  currentSchemaRef.current = variantSchema;
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
    getVariantTemplates(token).then(setVariantTemplates).catch(() => {});
  }, [token]);

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
      /* The rows are the truth; products.stock is derived from them since the resync landed. */
      const reconciledRows = seededRows;
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
        galleryImageUrls: product.galleryImageUrls ?? [],
      };
      const nextRows = toVariantRows(reconciledRows, currentSchemaRef.current);
      setForm(nextForm);
      setVariantRows(nextRows);
      setSelectedVariantTemplateId(product.variantTemplateId ?? null);
      previousSchemaRef.current = currentSchemaRef.current;
      /*
       * The warning that the rows had been rewritten to match products.stock. Nothing rewrites
       * them any more — the aggregate is recomputed from the variants on every movement — so
       * there is nothing left to warn about.
       */
      setStockSyncHint('');
      // Categories are initialized by the separate categories useEffect.
      // Do not reset them here — doing so would undo interactive toggles when the schema changes.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], product.variantTemplateId ?? null, currentSchemaRef.current));
    } else {
      const nextForm = { ...EMPTY_FORM };
      const nextRows = [createVariantRow(currentSchemaRef.current, EMPTY_FORM.stock)];
      setForm(nextForm);
      setVariantRows(nextRows);
      setSelectedVariantTemplateId(null);
      previousSchemaRef.current = currentSchemaRef.current;
      setStockSyncHint('');
      // Categories for new products are cleared by the dedicated product-change effect below.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], null, currentSchemaRef.current));
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
    setUnsavedConfirmOpen(false);
  }, [product]);

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
      const snapshotRows = toVariantRows(seededRows, variantSchema);
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
        galleryImageUrls: product.galleryImageUrls ?? [],
      };
      const fixedIds = withAncestors(ids, categories);
      setSelectedCatIds(fixedIds);
      // Keep initialSnapshot with original ids so form is dirty when parents were auto-added,
      // forcing the user to save and persist the corrected category selection.
      setInitialSnapshot(makeSnapshot(snapshotForm, snapshotRows, ids, product.variantTemplateId ?? null, variantSchema));
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
        let nextRawValues: string[];
        if (attribute.allowMultiple) {
          if (alreadySelected) {
            nextRawValues = currentValues.filter((value) => value !== optionValue);
          } else {
            nextRawValues = [...currentValues.filter((value) => value !== 'UNICO'), optionValue];
          }
        } else {
          nextRawValues = alreadySelected ? [] : [optionValue];
        }
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
    const e = validateProductForm({
      form, variantRows, variantSchema, primaryAttribute, secondaryAttribute,
    });
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function makeSnapshot(
    nextForm: typeof form,
    nextRows: VariantRow[],
    nextCatIds: string[],
    nextTemplateId: string | null,
    schema: VariantSchema,
  ): string {
    const variants = normalizeVariantRows(nextRows, schema)
      .map((variant) => ({
        color: variant.color.trim().toLowerCase(),
        size: variant.size,
        stock: Number(variant.stock),
      }))
      .sort((a, b) => `${a.color}::${a.size}`.localeCompare(`${b.color}::${b.size}`));
    // Explicitly ordered, and deliberately not by locale: this is a key for comparing snapshots,
    // so it has to be the same string on every machine.
    const cats = [...nextCatIds].sort(compareIds);
    return JSON.stringify({
      name: nextForm.name.trim(),
      description: nextForm.description.trim(),
      amount: nextForm.amount.trim(),
      listAmount: nextForm.listAmount.trim(),
      imageUrl: nextForm.imageUrl.trim(),
      condition: nextForm.condition,
      brand: nextForm.brand.trim(),
      active: nextForm.active,
      galleryImageUrls: nextForm.galleryImageUrls,
      variantTemplateId: nextTemplateId,
      categories: cats,
      variants,
    });
  }

  const currentSnapshot = makeSnapshot(form, variantRows, selectedCatIds, selectedVariantTemplateId, variantSchema);
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

  // Mounted only while open, so the ref callback fires exactly when it needs to.
  const setConfirmDialogRef = useCallback((node: HTMLDialogElement | null) => {
    confirmDialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);

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
        galleryImageUrls: form.galleryImageUrls,
        categoryIds: selectedCatIds,
        variantTemplateId: selectedVariantTemplateId || undefined,
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
      const message = err instanceof Error ? err.message : 'Error al guardar';
      // Inline for whoever is still looking at the field, and upward so the panel can say it out
      // loud: a save that fails silently is indistinguishable from one that worked.
      setApiError(message);
      onSaveFailed?.(message);
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
    let ext = 'jpg';
    if (blob.type.includes('png')) {
      ext = 'png';
    } else if (blob.type.includes('webp')) {
      ext = 'webp';
    }
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


  const inputClass =
    'w-full font-sans text-[0.82rem] border border-pe-charcoal/30 px-2.5 py-1.5 bg-pe-white text-pe-black placeholder-pe-charcoal/40 focus:outline-hidden focus:border-pe-rose focus:ring-1 focus:ring-pe-rose/25 transition-colors';
  const labelClass = 'block font-sans text-[0.68rem] tracking-[0.14em] uppercase text-pe-charcoal mb-1';
  const errorClass = 'font-sans text-xs text-pe-danger-ink mt-1';

  const categoryTree = buildCategoryTree(categories);

  return (
    <>
      <dialog
        ref={setDialogRef}
        onCancel={(event) => {
          event.preventDefault();
          handleAttemptClose();
        }}
        onClick={(event) => {
          if (event.target === dialogRef.current) handleAttemptClose();
        }}
        className="m-auto max-w-xl max-h-[92vh] w-[calc(100%-1rem)] sm:w-[calc(100%-2rem)] overflow-y-auto p-3 sm:p-5 border border-pe-charcoal/20 bg-pe-offwhite shadow-2xl backdrop:bg-pe-black/68"
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-display text-pe-black text-xl font-light">
            {product ? 'Editar Producto' : 'Nuevo Producto'}
          </h2>
          <button
            type="button"
            onClick={handleAttemptClose}
            className="inline-flex items-center justify-center w-8 h-8 text-pe-charcoal/40 hover:text-pe-rose-ink transition-colors"
            aria-label="Cerrar formulario"
          >
            <X size={16} />
          </button>
        </div>

        {apiError && <div className="bg-pe-danger-surface border border-pe-danger/40 text-pe-danger-ink text-sm px-4 py-2 mb-4">{apiError}</div>}
        {aiInfo && <div className="bg-pe-positive-surface border border-pe-positive/40 text-pe-positive-ink text-sm px-4 py-2 mb-4">{aiInfo}</div>}

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
              <span className={labelClass}>
                Condicion
              </span>
              <div className="grid grid-cols-2 border border-pe-charcoal/30 bg-pe-white">
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
                        ? 'bg-pe-rose text-white'
                        : 'text-pe-muted hover:bg-pe-rose/10',
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
              <p className="font-sans text-[0.66rem] text-pe-muted mt-1">
                Se calcula automaticamente desde {primaryAttribute.label.toLowerCase()} + {secondaryAttribute.label.toLowerCase()} + stock por variante.
              </p>
            </div>
            <div className="flex flex-col justify-end pb-1 gap-2">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-pe-rose"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                <span className="font-sans text-sm text-pe-black select-none">Activo</span>
              </div>
            </div>
          </div>

          <div>
            <label htmlFor="pf-variant-template" className={labelClass}>
              Tipo de Variante
            </label>
            <select
              id="pf-variant-template"
              className={inputClass}
              value={selectedVariantTemplateId ?? ''}
              onChange={(e) => setSelectedVariantTemplateId(e.target.value || null)}
            >
              <option value="">Generico (Variante + Detalle)</option>
              {variantTemplates.map((t) => (
                <option key={t.id} value={t.id}>{t.name}</option>
              ))}
            </select>
          </div>

          <div className="border border-pe-charcoal/12 bg-pe-white p-3 space-y-3">
            <div className="flex items-center justify-between">
              <p className={labelClass + ' mb-0'}>{variantSchema.title}</p>
              <button
                type="button"
                onClick={addVariantRow}
                className="inline-flex items-center gap-1.5 border border-pe-rose/40 text-pe-rose-ink font-sans text-[0.66rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:bg-pe-rose/10 transition-colors"
              >
                + Agregar
              </button>
            </div>

            <div className="space-y-2">
              {variantRows.map((row, index) => (
                <div key={row.id} className="grid grid-cols-1 sm:grid-cols-12 gap-2 items-start border border-pe-charcoal/10 p-2">
                  <div className="sm:col-span-3 space-y-1">
                    <p className={labelClass + ' mb-0'}>{primaryAttribute.label}</p>
                    <VariantTextValueInput
                      className={inputClass}
                      placeholder={primaryAttribute.placeholder ?? primaryAttribute.label}
                      value={getAttributeValue(row.attributes, primaryAttribute)}
                      onCommit={(raw) => updateVariantAttributeValue(index, primaryAttribute, raw)}
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
                                  ? 'border-pe-rose bg-pe-rose/12 text-pe-rose-ink'
                                  : 'border-pe-charcoal/20 text-pe-muted hover:border-pe-rose/40',
                              ].join(' ')}
                            >
                              {option.label}
                            </button>
                          );
                        })}
                      </div>
                    )}
                    {(secondaryAttribute.type === 'text' || secondaryAttribute.allowCustom) && (
                      <VariantTextValueInput
                        className={inputClass}
                        placeholder={secondaryAttribute.placeholder ?? secondaryAttribute.label}
                        value={getAttributeValue(row.attributes, secondaryAttribute)}
                        onCommit={(raw) => updateVariantAttributeValue(index, secondaryAttribute, raw)}
                      />
                    )}
                    <p className="font-sans text-[0.62rem] text-pe-muted">
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
                      className="w-full h-[36px] inline-flex items-center justify-center border border-pe-danger/40 text-pe-danger-ink text-[0.62rem] uppercase tracking-[0.1em] px-2 whitespace-nowrap hover:bg-pe-danger-surface transition-colors"
                    >
                      Quitar
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <p className="font-sans text-[0.62rem] text-pe-muted">
              {secondaryAttribute.allowMultiple
                ? `Puedes seleccionar varias ${secondaryAttribute.label.toLowerCase()}s por fila. Se guardan como valor compuesto.`
                : `La interfaz se adapta segun la metadata de categoria, sin asumir talla o color fijos.`}
            </p>

            {stockSyncHint && (
              <p className="font-sans text-[0.68rem] text-pe-warning-ink">
                {stockSyncHint}
              </p>
            )}
            {errors.combinations && <p className={errorClass}>{errors.combinations}</p>}
          </div>

          <div>
            <ImageDropzone
              label="Imagen del producto"
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
            <ProductGalleryEditor
              value={form.galleryImageUrls}
              onChange={(next) => setForm((prev) => ({ ...prev, galleryImageUrls: next }))}
              coverUrl={form.imageUrl}
              onCoverChange={(url) => setForm((prev) => ({ ...prev, imageUrl: url }))}
              token={token ?? ''}
            />
          </div>

          <div className="border border-pe-charcoal/12 bg-pe-white p-3 space-y-2">
            <button
              type="button"
              onClick={() => setAiToolsOpen((prev) => !prev)}
              className="w-full flex items-center justify-between text-left"
              aria-expanded={aiToolsOpen}
            >
              <span className="font-sans text-sm text-pe-black">Utilitarios IA</span>
              {aiToolsOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </button>
            {aiToolsOpen && (
              <div className="space-y-2">
                <button
                  type="button"
                  onClick={() => void handleInferWithAi()}
                  disabled={aiRunning}
                  className="inline-flex items-center gap-2 border border-pe-rose/35 text-pe-rose-ink font-sans text-[0.68rem] tracking-[0.1em] uppercase px-3 py-2 hover:bg-pe-rose/8 transition-colors disabled:opacity-50"
                >
                  {aiRunning ? <Loader2 size={13} className="animate-spin" /> : null}
                  {aiRunning ? 'Procesando IA...' : 'Inferir texto con IA'}
                </button>

                <div>
                  <label htmlFor="pf-ai-transform-prompt" className={labelClass + ' mb-1'}>Prompt transformacion (opcional)</label>
                  <textarea
                    id="pf-ai-transform-prompt"
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
                  className="inline-flex items-center gap-2 border border-pe-rose/35 text-pe-rose-ink font-sans text-[0.68rem] tracking-[0.1em] uppercase px-3 py-2 hover:bg-pe-rose/8 transition-colors disabled:opacity-50"
                >
                  {aiTransformRunning ? <Loader2 size={13} className="animate-spin" /> : null}
                  {aiTransformRunning ? 'Transformando...' : 'Transformar imagen con IA'}
                </button>

                {aiTransformPreviewUrl && (
                  <div className="border border-pe-charcoal/12 bg-pe-white p-2.5 space-y-2">
                    <p className="font-sans text-[0.68rem] tracking-[0.08em] uppercase text-pe-muted">
                      Preview transformada
                    </p>
                    <img
                      src={aiTransformPreviewUrl}
                      alt="Previsualizacion transformada"
                      className="w-full max-w-[220px] h-auto border border-pe-charcoal/15"
                    />
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={handleApplyTransformPreview}
                        className="inline-flex items-center gap-2 border border-pe-rose/35 text-pe-rose-ink font-sans text-[0.64rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:bg-pe-rose/8 transition-colors"
                      >
                        Reemplazar imagen actual
                      </button>
                      <a
                        href={aiTransformPreviewUrl}
                        download
                        className="inline-flex items-center gap-2 border border-pe-charcoal/20 text-pe-charcoal font-sans text-[0.64rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:border-pe-rose hover:text-pe-rose-ink transition-colors"
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
                    <span className="font-sans text-[0.65rem] text-pe-rose-ink">
                      {selectedCatIds.length} {selectedCatIds.length === 1 ? 'seleccionada' : 'seleccionadas'}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={expandAllCategories}
                    className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted hover:text-pe-rose-ink transition-colors"
                  >
                    Expandir
                  </button>
                  <span className="font-sans text-[0.6rem] text-pe-muted">|</span>
                  <button
                    type="button"
                    onClick={collapseAllCategories}
                    className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted hover:text-pe-rose-ink transition-colors"
                  >
                    Contraer
                  </button>
                </div>
              </div>
              <div className="border border-pe-charcoal/20 bg-pe-white py-1.5 max-h-60 overflow-y-auto">
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
              <p className="font-sans text-[0.6rem] text-pe-muted mt-1">
                Al seleccionar una subcategoría, su categoría padre se marca automáticamente.
              </p>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-2.5 mt-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 inline-flex items-center justify-center gap-1.5 bg-pe-rose text-white font-sans text-xs tracking-widest uppercase py-2.5 hover:bg-pe-rose-deep transition-colors disabled:opacity-50"
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
              className="flex-1 inline-flex items-center justify-center gap-1.5 border border-pe-charcoal/25 text-pe-black font-sans text-xs tracking-widest uppercase py-2.5 hover:border-pe-rose-ink hover:text-pe-rose-ink transition-colors"
            >
              <X size={14} />
              Cancelar
            </button>
          </div>
        </form>
      </dialog>
      {unsavedConfirmOpen && (
        <dialog
          ref={setConfirmDialogRef}
          onCancel={(event) => {
            event.preventDefault();
            setUnsavedConfirmOpen(false);
          }}
          onClick={(event) => {
            if (event.target === confirmDialogRef.current) setUnsavedConfirmOpen(false);
          }}
          className="m-auto max-w-sm w-[calc(100%-2rem)] border border-pe-charcoal/20 bg-pe-offwhite shadow-2xl p-4 sm:p-5 backdrop:bg-black/55"
        >
          <h3 className="font-display text-xl text-pe-black mb-2">
            Salir sin guardar
          </h3>
          <p className="font-sans text-[0.82rem] text-pe-charcoal leading-relaxed">
            Tienes cambios sin guardar en este producto. Si sales ahora, los cambios se perderan.
          </p>
          <div className="mt-4 flex flex-col sm:flex-row gap-2">
            <button
              type="button"
              onClick={() => setUnsavedConfirmOpen(false)}
              className="flex-1 inline-flex items-center justify-center border border-pe-charcoal/25 text-pe-black font-sans text-[0.68rem] tracking-[0.1em] uppercase py-2 hover:border-pe-rose-ink hover:text-pe-rose-ink transition-colors"
            >
              Seguir editando
            </button>
            <button
              type="button"
              onClick={handleDiscardAndClose}
              className="flex-1 inline-flex items-center justify-center border border-pe-danger/40 text-pe-danger-ink font-sans text-[0.68rem] tracking-[0.1em] uppercase py-2 hover:bg-pe-danger-surface transition-colors"
            >
              Salir sin guardar
            </button>
          </div>
        </dialog>
      )}
    </>
  );
}


