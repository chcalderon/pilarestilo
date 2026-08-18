import type { CategoryDto, CategoryType, ProductDto, ProductVariantDto } from './api';

export interface VariantAttributeOption {
  value: string;
  label: string;
  position: number;
}

export interface CategoryAttributeDefinition {
  code: string;
  label: string;
  type: 'text' | 'choice';
  options: VariantAttributeOption[];
  required: boolean;
  position: number;
  allowMultiple?: boolean;
  allowCustom?: boolean;
  placeholder?: string;
  legacyField: 'color' | 'size';
  defaultValues?: string[];
  summaryJoiner?: string;
}

export interface VariantSchema {
  key: CategoryType;
  /** What the product is, in the words the shop uses. The picker lists this, not the enum. */
  noun: string;
  title: string;
  attributes: [CategoryAttributeDefinition, CategoryAttributeDefinition];
}

export type VariantAttributeSelections = Record<string, string[]>;
export type VariantAttributeRecord = Record<string, string>;

const APPAREL_SIZE_OPTIONS = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'];
const SHOE_NUMBER_OPTIONS = ['34', '35', '36', '37', '38', '39', '40', '41', '42', '43'];

function optionList(values: string[]): VariantAttributeOption[] {
  return values.map((value, index) => ({ value, label: value, position: index }));
}

/**
 * The pair used by everything that is not a garment, a shoe or a piece of jewellery: a free primary
 * value and a free detail. Four category types share it — they differ only in the examples they
 * suggest, and writing them out four times is what made the picker look like it had duplicates.
 */
function variantAndDetail(
  key: CategoryType,
  noun: string,
  codes: [string, string],
  placeholders: [string, string],
  detailDefaults?: string[],
): VariantSchema {
  return {
    key,
    noun,
    title: 'Variante + detalle + stock',
    attributes: [
      {
        code: codes[0],
        label: 'Variante',
        type: 'text',
        options: [],
        required: true,
        position: 0,
        allowCustom: true,
        placeholder: placeholders[0],
        legacyField: 'color',
      },
      {
        code: codes[1],
        label: 'Detalle',
        type: 'text',
        options: [],
        required: true,
        position: 1,
        allowCustom: true,
        placeholder: placeholders[1],
        legacyField: 'size',
        ...(detailDefaults ? { defaultValues: detailDefaults } : {}),
      },
    ],
  };
}

const SCHEMAS: Record<CategoryType, VariantSchema> = {
  GENERIC: variantAndDetail(
    'GENERIC', 'Otro', ['primary', 'secondary'],
    ['Ej: Negro, Dorado, Cuero', 'Ej: Unico, Mini, Trenzado'], ['UNICO'],
  ),
  CLOTHING: {
    key: 'CLOTHING',
    noun: 'Prenda',
    title: 'Color + talla + stock',
    attributes: [
      {
        code: 'color',
        label: 'Color',
        type: 'text',
        options: [],
        required: true,
        position: 0,
        allowCustom: true,
        placeholder: 'Ej: Negro',
        legacyField: 'color',
      },
      {
        code: 'size',
        label: 'Talla',
        type: 'choice',
        options: optionList(APPAREL_SIZE_OPTIONS),
        required: true,
        position: 1,
        allowMultiple: true,
        allowCustom: true,
        placeholder: 'Otra talla',
        legacyField: 'size',
        defaultValues: ['UNICO'],
        summaryJoiner: '-',
      },
    ],
  },
  SHOES: {
    key: 'SHOES',
    noun: 'Zapato',
    title: 'Color + numero + stock',
    attributes: [
      {
        code: 'color',
        label: 'Color',
        type: 'text',
        options: [],
        required: true,
        position: 0,
        allowCustom: true,
        placeholder: 'Ej: Blanco',
        legacyField: 'color',
      },
      {
        code: 'number',
        label: 'Numero',
        type: 'choice',
        options: optionList(SHOE_NUMBER_OPTIONS),
        required: true,
        position: 1,
        allowCustom: true,
        placeholder: 'Ej: 38',
        legacyField: 'size',
      },
    ],
  },
  JEWELRY: {
    key: 'JEWELRY',
    noun: 'Bisuteria',
    title: 'Material + diseno + stock',
    attributes: [
      {
        code: 'material',
        label: 'Material',
        type: 'text',
        options: [],
        required: true,
        position: 0,
        allowCustom: true,
        placeholder: 'Ej: Plata 925',
        legacyField: 'color',
      },
      {
        code: 'design',
        label: 'Diseno',
        type: 'text',
        options: [],
        required: true,
        position: 1,
        allowCustom: true,
        placeholder: 'Ej: Eslabon clasico',
        legacyField: 'size',
      },
    ],
  },
  ACCESSORY: variantAndDetail(
    'ACCESSORY', 'Accesorio', ['variant', 'detail'],
    ['Ej: Negro, Cuero, Gold', 'Ej: Unico, Mini, Trenzado'], ['UNICO'],
  ),
  COLLECTION: variantAndDetail(
    'COLLECTION', 'Coleccion', ['variant', 'detail'], ['Ej: Look 01', 'Ej: Editorial'],
  ),
  SEASON: variantAndDetail(
    'SEASON', 'Temporada', ['variant', 'detail'], ['Ej: Invierno', 'Ej: Capsula 01'],
  ),
};

const CATEGORY_TYPE_PRIORITY: CategoryType[] = [
  'SHOES',
  'JEWELRY',
  'CLOTHING',
  'ACCESSORY',
  'COLLECTION',
  'SEASON',
  'GENERIC',
];

function normalizeToken(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

function buildOptionIndex(attribute: CategoryAttributeDefinition): Map<string, VariantAttributeOption> {
  return new Map(attribute.options.map((option) => [option.value.toLowerCase(), option]));
}

function sortAttributeValues(attribute: CategoryAttributeDefinition, values: string[]): string[] {
  const unique = Array.from(new Set(values.map(normalizeToken).filter(Boolean)));
  const optionIndex = buildOptionIndex(attribute);
  return unique.sort((left, right) => {
    const leftOption = optionIndex.get(left.toLowerCase());
    const rightOption = optionIndex.get(right.toLowerCase());
    if (leftOption || rightOption) {
      return (leftOption?.position ?? 999) - (rightOption?.position ?? 999);
    }
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
      return leftNumber - rightNumber;
    }
    return left.localeCompare(right, 'es', { sensitivity: 'base', numeric: true });
  });
}

export function getVariantSchema(categoryType?: CategoryType | null): VariantSchema {
  if (!categoryType) return SCHEMAS.GENERIC;
  return SCHEMAS[categoryType] ?? SCHEMAS.GENERIC;
}

export function getPrimaryAttribute(schema: VariantSchema): CategoryAttributeDefinition {
  return schema.attributes[0];
}

export function getSecondaryAttribute(schema: VariantSchema): CategoryAttributeDefinition {
  return schema.attributes[1];
}

export function getAttributeValue(
  selections: VariantAttributeSelections,
  attribute: CategoryAttributeDefinition,
): string {
  return selections[attribute.code]?.[0] ?? '';
}

export function getAttributeValues(
  selections: VariantAttributeSelections,
  attribute: CategoryAttributeDefinition,
): string[] {
  return selections[attribute.code] ?? [];
}

export function createEmptyVariantSelections(schema: VariantSchema): VariantAttributeSelections {
  return Object.fromEntries(
    schema.attributes.map((attribute) => [
      attribute.code,
      sortAttributeValues(attribute, attribute.defaultValues ?? []),
    ]),
  );
}

export function normalizeAttributeValues(
  attribute: CategoryAttributeDefinition,
  rawValues: string[],
): string[] {
  const optionIndex = buildOptionIndex(attribute);
  const canonical = rawValues
    .map(normalizeToken)
    .filter(Boolean)
    .map((value) => optionIndex.get(value.toLowerCase())?.value ?? value);
  return sortAttributeValues(attribute, canonical);
}

function composeStoredAttributeValue(
  attribute: CategoryAttributeDefinition,
  values: string[],
): string {
  const normalized = normalizeAttributeValues(attribute, values);
  if (normalized.length === 0) return '';
  if (attribute.allowMultiple) {
    return normalized.join(attribute.summaryJoiner ?? '-');
  }
  return normalized[0];
}

function parseStoredAttributeValue(
  attribute: CategoryAttributeDefinition,
  rawValue: string | null | undefined,
): string[] {
  const value = normalizeToken(rawValue ?? '');
  if (!value) {
    return sortAttributeValues(attribute, attribute.defaultValues ?? []);
  }
  const parts = attribute.allowMultiple
    ? value.split(attribute.summaryJoiner ?? '-').map(normalizeToken)
    : [value];
  return normalizeAttributeValues(attribute, parts);
}

export function legacyVariantToSelections(
  variant: ProductVariantDto,
  schema: VariantSchema,
): VariantAttributeSelections {
  const entries = schema.attributes.map((attribute) => {
    const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
    return [attribute.code, parseStoredAttributeValue(attribute, raw)] as const;
  });
  return Object.fromEntries(entries);
}

export function selectionsToLegacyVariant(
  selections: VariantAttributeSelections,
  stock: number,
  schema: VariantSchema,
): ProductVariantDto {
  const primary = getPrimaryAttribute(schema);
  const secondary = getSecondaryAttribute(schema);
  const color = composeStoredAttributeValue(primary, selections[primary.code] ?? []);
  const size = composeStoredAttributeValue(secondary, selections[secondary.code] ?? []);
  return {
    color,
    size,
    stock,
    stockOnHand: stock,
    stockReserved: 0,
    stockAvailable: stock,
  };
}

export function toVariantAttributeRecord(
  variant: ProductVariantDto,
  schema: VariantSchema,
): VariantAttributeRecord {
  return Object.fromEntries(
    schema.attributes.map((attribute) => {
      const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
      return [attribute.code, composeStoredAttributeValue(attribute, parseStoredAttributeValue(attribute, raw))];
    }),
  );
}

export function summarizeVariantAttributeValues(
  variants: ProductVariantDto[] | undefined,
  schema: VariantSchema,
  attributeCode: string,
): string {
  if (!Array.isArray(variants) || variants.length === 0) return '';
  const attribute = schema.attributes.find((item) => item.code === attributeCode);
  if (!attribute) return '';
  const values = variants.flatMap((variant) => {
    const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
    return parseStoredAttributeValue(attribute, raw);
  });
  const normalized = sortAttributeValues(attribute, values);
  if (normalized.length === 0) return '';
  return normalized.join(attribute.summaryJoiner ?? ' / ');
}

function pickCategoryType(categoryTypes: CategoryType[] | undefined): CategoryType | null {
  if (!Array.isArray(categoryTypes) || categoryTypes.length === 0) return null;
  const normalized = Array.from(
    new Set(
      categoryTypes.filter((type): type is CategoryType => Boolean(type)),
    ),
  );
  if (normalized.length === 0) return null;

  return (
    CATEGORY_TYPE_PRIORITY.find((candidate) => normalized.includes(candidate))
    ?? normalized[0]
    ?? null
  );
}

function buildCategoryDepthMap(categories: CategoryDto[]): Map<string, number> {
  const byId = new Map(categories.map((category) => [category.id, category]));
  const depthMap = new Map<string, number>();
  const depthOf = (id: string): number => {
    const cached = depthMap.get(id);
    if (cached != null) return cached;
    const current = byId.get(id);
    if (!current || !current.parentId) {
      depthMap.set(id, 0);
      return 0;
    }
    const depth = depthOf(current.parentId) + 1;
    depthMap.set(id, depth);
    return depth;
  };
  for (const category of categories) {
    depthOf(category.id);
  }
  return depthMap;
}

export function resolvePreferredCategoryType(params: {
  categoryTypes?: CategoryType[];
  categoryIds?: string[];
  categories?: CategoryDto[];
}): CategoryType {
  const directType = pickCategoryType(params.categoryTypes);
  if (directType) return directType;

  if (!params.categoryIds?.length || !params.categories?.length) {
    return 'GENERIC';
  }

  const depthMap = buildCategoryDepthMap(params.categories);
  const selected = params.categories
    .filter((category) => params.categoryIds?.includes(category.id))
    .sort((left, right) => {
      const depthDiff = (depthMap.get(right.id) ?? 0) - (depthMap.get(left.id) ?? 0);
      if (depthDiff !== 0) return depthDiff;
      return left.sortOrder - right.sortOrder;
    });

  const selectedTypes = selected
    .map((category) => category.categoryType)
    .filter((type): type is CategoryType => Boolean(type) && type !== 'GENERIC');

  return pickCategoryType(selectedTypes) ?? selected[0]?.categoryType ?? 'GENERIC';
}

/**
 * The attribute pair a product's variants use.
 *
 * <p>An explicit `variantType` wins. Without one the type is inferred from the categories, which
 * is what every product did before an admin could state it — and why moving a product between
 * categories used to relabel its variants without anyone asking.
 */
export function getProductVariantSchema(
  product?: Pick<ProductDto, 'categoryTypes' | 'variantType'> | null
): VariantSchema {
  const stated = product?.variantType;
  if (stated && stated in SCHEMAS) {
    return SCHEMAS[stated as CategoryType];
  }
  return getVariantSchema(pickCategoryType(product?.categoryTypes));
}

/**
 * Category types that describe how a product is grouped rather than what shape it is.
 *
 * <p>A department ("Mujer"), a collection and a season are all cross-cutting: a dress belongs to
 * "Mujer" and to "Verano" as naturally as it belongs to "Vestidos". Only the shape types —
 * clothing, shoes, jewellery, accessories — say what a product *is*, and a product is only ever
 * one of those, which is what makes them mutually exclusive.
 */
const GROUPING_TYPES: ReadonlySet<CategoryType> = new Set(['GENERIC', 'COLLECTION', 'SEASON']);

/** The shapes a product can be, which is exactly what the variant picker may offer. */
const SELECTABLE_VARIANT_TYPES: CategoryType[] = ['CLOTHING', 'SHOES', 'JEWELRY', 'ACCESSORY'];

/**
 * The categories selectable for a product whose variants use `variantType`.
 *
 * <p>Three ways in. A grouping category always qualifies. A shape category qualifies when it
 * matches. And a category qualifies when one of its descendants does — because the taxonomy nests
 * shapes inside each other: "Aros" is JEWELRY under "Accesorios", which is ACCESSORY. Selecting
 * the child forces the parent, so a rule that judged each category alone would have the form add
 * a category and then refuse to save it. Two products in this catalogue sit exactly there.
 *
 * <p>A category with no type stated counts as grouping: refusing it would lock an admin out of a
 * category nobody has classified yet.
 */
export function allowedCategoryIds(
  categories: CategoryDto[],
  variantType: CategoryType
): Set<string> {
  const allowed = new Set<string>();
  const childrenOf = new Map<string, CategoryDto[]>();
  for (const category of categories) {
    const key = category.parentId ?? '';
    const list = childrenOf.get(key);
    if (list) list.push(category);
    else childrenOf.set(key, [category]);
  }

  /** Qualifies on its own account, ignoring the tree. */
  const qualifiesAlone = (category: CategoryDto): boolean =>
    !category.categoryType
    || GROUPING_TYPES.has(category.categoryType)
    || category.categoryType === variantType;

  /* Depth-first: a branch is allowed when it holds anything allowed, so parents ride along. */
  const visit = (category: CategoryDto): boolean => {
    let anyDescendantAllowed = false;
    for (const child of childrenOf.get(category.id) ?? []) {
      if (visit(child)) anyDescendantAllowed = true;
    }
    const ok = qualifiesAlone(category) || anyDescendantAllowed;
    if (ok) allowed.add(category.id);
    return ok;
  };

  for (const root of childrenOf.get('') ?? []) visit(root);

  /* Orphans — a parentId pointing at a category not in this list — are judged on their own. */
  for (const category of categories) {
    if (!allowed.has(category.id) && qualifiesAlone(category)) allowed.add(category.id);
  }

  return allowed;
}

/**
 * The variant shapes an admin can choose, in the order the picker lists them.
 *
 * <p>Deliberately not every {@link CategoryType}. A collection, a season and an unclassified
 * category are ways of *grouping* products, not shapes a product has — the same distinction
 * {@link GROUPING_TYPES} already draws for categories. Offering them here listed four options that
 * all read "Variante / Detalle", which is how the picker came to look like it had duplicates. They
 * remain as schemas because a product whose only category is a collection still has to render
 * something.
 */
export function listSelectableVariantSchemas(): VariantSchema[] {
  return SELECTABLE_VARIANT_TYPES.map((type) => SCHEMAS[type]);
}

/** True when a stored `variantType` is one the picker still offers. */
export function isSelectableVariantType(type: CategoryType): boolean {
  return SELECTABLE_VARIANT_TYPES.includes(type);
}

