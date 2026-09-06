import { describe, expect, it } from 'vitest';
import { validateProductForm, type ValidateProductFormArgs } from '../ProductForm';
import {
  buildVariantSchema,
  getPrimaryAttribute,
  getSecondaryAttribute,
  createEmptyVariantSelections,
} from '@/lib/variantSchema';
import type { VariantFieldConfigDto } from '@/lib/api';

/**
 * Unit tests for validate()'s extracted pure form, written before splitting it into
 * validateBasicFields/validateVariantRows (S3776, complexity 38) -- it had none. A pure function
 * taking everything as arguments needs no component rendering, so this is a plain unit test rather
 * than a characterization test with mocks.
 */

const schema = buildVariantSchema(null);
const primaryAttribute = getPrimaryAttribute(schema);
const secondaryAttribute = getSecondaryAttribute(schema);

function baseForm() {
  return {
    name: 'Vestido',
    description: 'Un vestido bonito',
    amount: '20000',
    listAmount: '',
    currency: 'CLP',
    imageUrl: '',
    condition: 'NEW' as const,
    brand: 'PilarEstilo',
    stock: '1',
    active: true,
    galleryImageUrls: [] as string[],
  };
}

function variantRow(primary = 'Negro', secondary = 'UNICO', stock = '5') {
  return {
    id: crypto.randomUUID(),
    attributes: {
      ...createEmptyVariantSelections(schema),
      [primaryAttribute.code]: [primary],
      [secondaryAttribute.code]: [secondary],
    },
    stock,
  };
}

function baseArgs(overrides: Partial<ValidateProductFormArgs> = {}): ValidateProductFormArgs {
  return {
    form: baseForm(),
    variantRows: [variantRow()],
    variantSchema: schema,
    primaryAttribute,
    secondaryAttribute,
    ...overrides,
  };
}

describe('validateProductForm', () => {
  it('accepts a fully valid form', () => {
    expect(validateProductForm(baseArgs())).toEqual({});
  });

  it('flags missing name, brand and description', () => {
    const errors = validateProductForm(baseArgs({ form: { ...baseForm(), name: ' ', brand: '', description: '' } }));
    expect(errors.name).toBeDefined();
    expect(errors.brand).toBeDefined();
    expect(errors.description).toBeDefined();
  });

  it('flags a missing or non-positive price', () => {
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '' } })).amount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '0' } })).amount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: 'abc' } })).amount).toBeDefined();
  });

  it('flags an invalid list price, and one that does not beat the sale price', () => {
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), listAmount: 'abc' } })).listAmount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '20000', listAmount: '15000' } })).listAmount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '20000', listAmount: '25000' } })).listAmount).toBeUndefined();
  });

  it('requires at least one variant row', () => {
    expect(validateProductForm(baseArgs({ variantRows: [] })).combinations).toMatch(/al menos una combinacion/i);
  });

  it('flags a required attribute missing on a specific row', () => {
    const rowMissingPrimary = {
      id: crypto.randomUUID(),
      attributes: { ...createEmptyVariantSelections(schema), [secondaryAttribute.code]: ['UNICO'] },
      stock: '5',
    };
    const errors = validateProductForm(baseArgs({ variantRows: [rowMissingPrimary] }));
    expect(errors.combinations).toMatch(new RegExp(`${primaryAttribute.label}.*fila 1`, 'i'));
  });

  it('flags an invalid stock value on a specific row', () => {
    const errors = validateProductForm(baseArgs({ variantRows: [variantRow('Negro', 'UNICO', 'abc')] }));
    expect(errors.combinations).toMatch(/stock valido requerido en fila 1/i);
  });

  it('flags duplicate color+detail combinations across rows', () => {
    const errors = validateProductForm(baseArgs({
      variantRows: [variantRow('Negro', 'UNICO'), variantRow('negro', 'UNICO')],
    }));
    expect(errors.combinations).toMatch(/no se permiten combinaciones duplicadas/i);
  });

  it('allows two rows that differ only by case-insensitive color are still the same key (case folded)', () => {
    const errors = validateProductForm(baseArgs({
      variantRows: [variantRow('Negro', 'UNICO'), variantRow('Rojo', 'UNICO')],
    }));
    expect(errors.combinations).toBeUndefined();
  });
});

/**
 * Regression: a template-backed product (options list on the secondary field) whose stored
 * composite value is in a different but equivalent order must still save.
 *
 * "Abrigo Teddy elegante" stores size "S-M". On mount, before getVariantTemplates resolves, the
 * form runs on the generic fallback schema (no options), which sorts a split composite
 * alphabetically -> ["M", "S"]. The real template then loads without changing variantSchema.key
 * (it is already the template id), so the rows are never re-sorted to option order ["S", "M"].
 * validate() used to compare the raw array against its normalized form and reject any difference
 * as "Talla invalido en fila 1" -- blocking the save with no network call. Order/dedupe/case are
 * re-normalized by normalizeVariantRows on save, so only genuinely unknown values in a strict
 * (no allowCustom) options field are a real error.
 */
const CLOTHING_CONFIG: VariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};

describe('validateProductForm: composite variant value whose order differs from the schema', () => {
  const clothing = buildVariantSchema(CLOTHING_CONFIG);
  const clothingPrimary = getPrimaryAttribute(clothing);
  const clothingSecondary = getSecondaryAttribute(clothing);

  function clothingRow(secondaryValues: string[]) {
    return {
      id: crypto.randomUUID(),
      attributes: {
        ...createEmptyVariantSelections(clothing),
        [clothingPrimary.code]: ['Blanco tornasol'],
        [clothingSecondary.code]: secondaryValues,
      },
      stock: '1',
    };
  }

  function clothingArgs(rows: ReturnType<typeof clothingRow>[]): ValidateProductFormArgs {
    return {
      form: baseForm(),
      variantRows: rows,
      variantSchema: clothing,
      primaryAttribute: clothingPrimary,
      secondaryAttribute: clothingSecondary,
    };
  }

  it('accepts a composite size stored out of option order (["M","S"] vs option order ["S","M"])', () => {
    expect(validateProductForm(clothingArgs([clothingRow(['M', 'S'])])).combinations).toBeUndefined();
  });

  it('accepts a custom size when the options field allows custom values', () => {
    expect(validateProductForm(clothingArgs([clothingRow(['Talla especial'])])).combinations).toBeUndefined();
  });

  it('rejects an unknown value when the options field forbids custom values', () => {
    const strict = buildVariantSchema({
      ...CLOTHING_CONFIG,
      secondary: { ...CLOTHING_CONFIG.secondary, allowCustom: false },
    });
    const strictSecondary = getSecondaryAttribute(strict);
    const row = {
      id: crypto.randomUUID(),
      attributes: {
        ...createEmptyVariantSelections(strict),
        [getPrimaryAttribute(strict).code]: ['Blanco tornasol'],
        [strictSecondary.code]: ['Inventada'],
      },
      stock: '1',
    };
    const errors = validateProductForm({
      form: baseForm(),
      variantRows: [row],
      variantSchema: strict,
      primaryAttribute: getPrimaryAttribute(strict),
      secondaryAttribute: strictSecondary,
    });
    expect(errors.combinations).toMatch(/talla invalido/i);
  });
});
