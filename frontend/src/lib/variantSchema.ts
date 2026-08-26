import type { VariantFieldConfigDto, VariantFieldDto, ProductVariantDto } from './api';

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
  key: string;
  /** What the product is, in the words the shop uses. The picker lists this, not the enum. */
  noun: string;
  title: string;
  attributes: [CategoryAttributeDefinition, CategoryAttributeDefinition];
}

export type VariantAttributeSelections = Record<string, string[]>;
export type VariantAttributeRecord = Record<string, string>;

function optionList(values: string[]): VariantAttributeOption[] {
  return values.map((value, index) => ({ value, label: value, position: index }));
}

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

const GENERIC_FALLBACK: VariantFieldConfigDto = {
  primary: { label: 'Variante', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
  secondary: { label: 'Detalle', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
};

function optionsFor(field: VariantFieldDto): VariantAttributeOption[] {
  if (field.inputType === 'OPTIONS') {
    return optionList(field.options);
  }
  if (field.inputType === 'RANGE' && field.min != null && field.max != null) {
    const min = field.min;
    const max = field.max;
    return optionList(Array.from({ length: max - min + 1 }, (_, i) => String(min + i)));
  }
  return [];
}

function fieldToAttribute(
  field: VariantFieldDto,
  code: string,
  legacyField: 'color' | 'size',
): CategoryAttributeDefinition {
  const options = optionsFor(field);
  return {
    code,
    label: field.label,
    type: field.inputType === 'FREE_TEXT' ? 'text' : 'choice',
    options,
    required: true,
    position: code === 'primary' ? 0 : 1,
    allowMultiple: field.allowMultiple,
    allowCustom: field.allowCustom || field.inputType === 'FREE_TEXT',
    legacyField,
    summaryJoiner: field.allowMultiple ? '-' : undefined,
  };
}

/** Builds the two-attribute schema a variant template's (or the generic fallback's) config describes. */
export function buildVariantSchema(config: VariantFieldConfigDto | null, key = 'GENERIC'): VariantSchema {
  const resolved = config ?? GENERIC_FALLBACK;
  const secondaryAttribute = fieldToAttribute(resolved.secondary, 'secondary', 'size');
  if (!config) {
    // A product with no variant template is exactly the case the old GENERIC/ACCESSORY/
    // COLLECTION/SEASON schemas covered, and all of them pre-filled the detail field with
    // "UNICO" -- preserved here so an admin creating a variant for an unassigned product sees the
    // same starting point as before this rewrite.
    secondaryAttribute.defaultValues = ['UNICO'];
  }
  return {
    key,
    noun: 'Variante',
    title: `${resolved.primary.label} + ${resolved.secondary.label} + stock`,
    attributes: [
      fieldToAttribute(resolved.primary, 'primary', 'color'),
      secondaryAttribute,
    ],
  };
}
