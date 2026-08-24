import { describe, expect, it } from 'vitest';
import { validateProductForm, type ValidateProductFormArgs } from '../ProductForm';
import {
  getVariantSchema,
  getPrimaryAttribute,
  getSecondaryAttribute,
  createEmptyVariantSelections,
} from '@/lib/variantSchema';
import type { CategoryDto } from '@/lib/api';

/**
 * Unit tests for validate()'s extracted pure form, written before splitting it into
 * validateBasicFields/validateVariantRows/validateCategoryCompatibility (S3776, complexity 38) --
 * it had none. A pure function taking everything as arguments needs no component rendering, so
 * this is a plain unit test rather than a characterization test with mocks.
 */

const schema = getVariantSchema();
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
    variantType: '' as const,
  };
}

function variantRow(primary = 'Negro', secondary = 'UNICO', stock = '5') {
  return {
    attributes: {
      ...createEmptyVariantSelections(schema),
      [primaryAttribute.code]: [primary],
      [secondaryAttribute.code]: [secondary],
    },
    stock,
  };
}

function category(overrides: Partial<CategoryDto> = {}): CategoryDto {
  return {
    id: 'c1',
    slug: 'ropa',
    nameEs: 'Ropa',
    nameEn: 'Clothing',
    parentId: null,
    sortOrder: 0,
    active: true,
    featured: false,
    menuVisible: true,
    categoryType: 'GENERIC',
    ...overrides,
  };
}

function baseArgs(overrides: Partial<ValidateProductFormArgs> = {}): ValidateProductFormArgs {
  return {
    form: baseForm(),
    variantRows: [variantRow()],
    variantSchema: schema,
    categories: [],
    selectedCatIds: [],
    allowedCatIds: new Set(),
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

  it('flags a selected category that is incompatible with the chosen variant type', () => {
    const ropa = category({ id: 'c1', nameEs: 'Ropa' });
    const errors = validateProductForm(baseArgs({
      categories: [ropa],
      selectedCatIds: ['c1'],
      allowedCatIds: new Set(),
    }));
    expect(errors.categories).toMatch(/Ropa/);
  });

  it('does not flag a selected category that is compatible', () => {
    const ropa = category({ id: 'c1', nameEs: 'Ropa' });
    const errors = validateProductForm(baseArgs({
      categories: [ropa],
      selectedCatIds: ['c1'],
      allowedCatIds: new Set(['c1']),
    }));
    expect(errors.categories).toBeUndefined();
  });
});
