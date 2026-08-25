import { describe, expect, it } from 'vitest';
import {
  getPrimaryAttribute,
  getSecondaryAttribute,
  createEmptyVariantSelections,
  normalizeAttributeValues,
  selectionsToLegacyVariant,
  legacyVariantToSelections,
  toVariantAttributeRecord,
  summarizeVariantAttributeValues,
  buildVariantSchema,
  allowedCategoryIdsFor,
} from '../variantSchema';
import type { CategoryDto, CategoryVariantFieldConfigDto } from '../api';

/**
 * Characterization tests written before rewriting this module from a fixed
 * CategoryType-keyed lookup table to a config-driven one (Sonar-adjacent
 * work is not the reason here -- correctness of the multi-value/custom-value
 * composition logic, which the rewrite must not regress, is). Originally run
 * against the enum-driven implementation to lock in behavior; now run
 * against the config-driven rewrite with equivalent fixtures.
 */
const CLOTHING_CONFIG: CategoryVariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};

describe('variantSchema: multi-value composition (must survive the config-driven rewrite)', () => {
  it('composes multiple selected clothing sizes into one hyphen-joined stored value', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const selections = { [secondary.code]: ['S', 'M', 'L'] };
    const variant = selectionsToLegacyVariant(
      { [getPrimaryAttribute(schema).code]: ['Negro'], ...selections },
      3,
      schema,
    );
    expect(variant.size).toBe('S-M-L');
  });

  it('parses a stored hyphen-joined value back into separate selections', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const selections = legacyVariantToSelections(
      { color: 'Negro', size: 'S-M-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(selections[secondary.code].sort()).toEqual(['L', 'M', 'S']);
  });

  it('accepts a custom value alongside the fixed options list', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const normalized = normalizeAttributeValues(secondary, ['Talla especial']);
    expect(normalized).toEqual(['Talla especial']);
  });

  it('defaults to UNICO for the generic pair when nothing is selected', () => {
    const schema = buildVariantSchema(null);
    const selections = createEmptyVariantSelections(schema);
    const secondary = getSecondaryAttribute(schema);
    expect(selections[secondary.code]).toEqual(['UNICO']);
  });

  it('summarizes multiple variants secondary values, deduped and sorted', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const summary = summarizeVariantAttributeValues(
      [
        { color: 'Negro', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
        { color: 'Rojo', size: 'S-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      ],
      schema,
      secondary.code,
    );
    expect(summary).toBe('S-M-L');
  });

  it('round-trips a single-value field (color) without splitting on hyphen', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const record = toVariantAttributeRecord(
      { color: 'Azul-Marino', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(record[getPrimaryAttribute(schema).code]).toBe('Azul-Marino');
  });
});

describe('allowedCategoryIdsFor', () => {
  function shapeCategory(id: string): CategoryDto {
    return {
      id, slug: id, nameEs: id, nameEn: id, parentId: null, sortOrder: 0,
      active: true, featured: false, menuVisible: true, categoryType: 'GENERIC',
      definesVariantFields: true, variantFieldConfig: null,
    } as CategoryDto;
  }

  function groupingCategory(id: string): CategoryDto {
    return {
      id, slug: id, nameEs: id, nameEn: id, parentId: null, sortOrder: 0,
      active: true, featured: false, menuVisible: true, categoryType: 'GENERIC',
      definesVariantFields: false, variantFieldConfig: null,
    } as CategoryDto;
  }

  it('allows every shape category while none has been picked yet -- otherwise the first one could never be picked', () => {
    const categories = [shapeCategory('zapatos'), shapeCategory('aros')];
    const allowed = allowedCategoryIdsFor(categories, null);
    expect(allowed.has('zapatos')).toBe(true);
    expect(allowed.has('aros')).toBe(true);
  });

  it('locks every other shape category once one is picked', () => {
    const categories = [shapeCategory('zapatos'), shapeCategory('aros')];
    const allowed = allowedCategoryIdsFor(categories, 'zapatos');
    expect(allowed.has('zapatos')).toBe(true);
    expect(allowed.has('aros')).toBe(false);
  });

  it('always allows a grouping category, regardless of what shape is picked', () => {
    const categories = [shapeCategory('zapatos'), groupingCategory('mujer')];
    const allowed = allowedCategoryIdsFor(categories, 'zapatos');
    expect(allowed.has('mujer')).toBe(true);
  });

  /**
   * A shape category with a shape-category descendant that matches the current pick. The form
   * auto-selects ancestors, so judging each category alone would add the ancestor and then
   * refuse to save it -- the same deadlock the old enum-driven allowedCategoryIds guarded against
   * (nested "aros"/JEWELRY under "accesorios"/ACCESSORY in the real catalogue).
   */
  function nestedShapeTree(): CategoryDto[] {
    return [
      { ...shapeCategory('parent-shape'), id: 'parent-shape' },
      { ...shapeCategory('child-shape'), id: 'child-shape', parentId: 'parent-shape' },
      { ...shapeCategory('other-shape'), id: 'other-shape' },
    ];
  }

  it('allows a shape-category ancestor whose descendant is the one picked', () => {
    const allowed = allowedCategoryIdsFor(nestedShapeTree(), 'child-shape');
    expect(allowed.has('child-shape')).toBe(true);
    expect(allowed.has('parent-shape')).toBe(true);
    expect(allowed.has('other-shape')).toBe(false);
  });

  it('does not allow a shape-category ancestor whose descendants all mismatch', () => {
    const allowed = allowedCategoryIdsFor(nestedShapeTree(), 'other-shape');
    expect(allowed.has('parent-shape')).toBe(false);
    expect(allowed.has('child-shape')).toBe(false);
    expect(allowed.has('other-shape')).toBe(true);
  });

  it('judges an orphan (parentId pointing outside the list) on its own account', () => {
    const orphan = { ...shapeCategory('suelta'), parentId: 'no-existe' };
    expect(allowedCategoryIdsFor([orphan], 'suelta').has('suelta')).toBe(true);
    expect(allowedCategoryIdsFor([orphan], 'otra').has('suelta')).toBe(false);
  });

  it('handles an empty catalogue', () => {
    expect(allowedCategoryIdsFor([], null).size).toBe(0);
  });
});
