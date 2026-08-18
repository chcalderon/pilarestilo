import { describe, expect, it } from 'vitest';
import {
  allowedCategoryIds,
  getProductVariantSchema,
  isSelectableVariantType,
  listSelectableVariantSchemas,
} from '../variantSchema';
import type { CategoryDto, CategoryType } from '../api';

function category(
  id: string,
  categoryType: CategoryType | null,
  parentId: string | null = null
): CategoryDto {
  return {
    id,
    slug: id,
    nameEs: id,
    nameEn: id,
    parentId,
    categoryType: categoryType as CategoryType,
    sortOrder: 0,
  } as CategoryDto;
}

/**
 * The real catalogue, which is what the rule has to survive:
 *
 *   mujer (GENERIC)
 *   ├── accesorios (ACCESSORY)
 *   │   └── aros (JEWELRY)
 *   ├── vestidos (CLOTHING)
 *   ├── zapatos (SHOES)
 *   └── verano (SEASON)
 */
const catalogue: CategoryDto[] = [
  category('mujer', 'GENERIC'),
  category('accesorios', 'ACCESSORY', 'mujer'),
  category('aros', 'JEWELRY', 'accesorios'),
  category('vestidos', 'CLOTHING', 'mujer'),
  category('zapatos', 'SHOES', 'mujer'),
  category('verano', 'SEASON', 'mujer'),
];

describe('allowedCategoryIds', () => {
  it('allows the matching shape category', () => {
    expect(allowedCategoryIds(catalogue, 'CLOTHING')).toContain('vestidos');
    expect(allowedCategoryIds(catalogue, 'SHOES')).toContain('zapatos');
  });

  /** A product cannot be both a shoe and a dress, so its categories cannot say both. */
  it('refuses the shape categories that do not match', () => {
    const allowed = allowedCategoryIds(catalogue, 'CLOTHING');
    expect(allowed).not.toContain('zapatos');
    expect(allowed).not.toContain('aros');
  });

  it('always allows grouping categories', () => {
    for (const type of ['CLOTHING', 'SHOES', 'JEWELRY'] as const) {
      const allowed = allowedCategoryIds(catalogue, type);
      expect(allowed).toContain('mujer');
      expect(allowed).toContain('verano');
    }
  });

  /**
   * The case that made the first version of this rule deadlock. "Aros" is JEWELRY inside
   * "Accesorios", which is ACCESSORY, and the form auto-selects ancestors — so judging each
   * category alone had it add "accesorios" and then refuse to save it. Two products in the real
   * catalogue sit exactly here.
   */
  it('allows a parent whose descendant matches', () => {
    const allowed = allowedCategoryIds(catalogue, 'JEWELRY');

    expect(allowed).toContain('aros');
    expect(allowed).toContain('accesorios');
    expect(allowed).toContain('mujer');
  });

  /** That leniency is only for the path down to a match, not for every shape category. */
  it('does not allow a parent whose descendants all mismatch', () => {
    const allowed = allowedCategoryIds(catalogue, 'CLOTHING');
    expect(allowed).not.toContain('accesorios');
  });

  it('allows ACCESSORY itself when that is the chosen type', () => {
    const allowed = allowedCategoryIds(catalogue, 'ACCESSORY');
    expect(allowed).toContain('accesorios');
    expect(allowed).not.toContain('zapatos');
  });

  /** Refusing an unclassified category would lock the admin out of it for no stated reason. */
  it('allows a category with no type stated', () => {
    const withUntyped = [...catalogue, category('nueva', null, 'mujer')];
    expect(allowedCategoryIds(withUntyped, 'SHOES')).toContain('nueva');
  });

  /** A parentId pointing outside the list must not drop the category from the walk. */
  it('judges an orphan on its own account', () => {
    const orphans = [category('suelta', 'SHOES', 'no-existe')];
    expect(allowedCategoryIds(orphans, 'SHOES')).toContain('suelta');
    expect(allowedCategoryIds(orphans, 'CLOTHING')).not.toContain('suelta');
  });

  it('handles an empty catalogue', () => {
    expect(allowedCategoryIds([], 'CLOTHING').size).toBe(0);
  });
});

describe('getProductVariantSchema', () => {
  it('honours a stated type over the categories', () => {
    const schema = getProductVariantSchema({ categoryTypes: ['CLOTHING'], variantType: 'SHOES' });

    expect(schema.key).toBe('SHOES');
    expect(schema.attributes[1].label).toBe('Numero');
  });

  /** Every product predating the picker has no stated type and must keep working. */
  it('falls back to the categories when none is stated', () => {
    expect(getProductVariantSchema({ categoryTypes: ['SHOES'], variantType: null }).key).toBe('SHOES');
    expect(getProductVariantSchema({ categoryTypes: ['JEWELRY'] }).key).toBe('JEWELRY');
  });

  it('falls back again when the stated value is not a known schema', () => {
    const schema = getProductVariantSchema({
      categoryTypes: ['CLOTHING'],
      variantType: 'NOT_A_TYPE' as never,
    });

    expect(schema.key).toBe('CLOTHING');
  });
});

describe('the variant type picker', () => {
  it('offers no two options that read the same', () => {
    const labels = listSelectableVariantSchemas().map(
      (schema) => `${schema.attributes[0].label} / ${schema.attributes[1].label}`,
    );

    // Four category types share "Variante / Detalle", which is how the picker came to show what
    // looked like duplicates. Only one of them may be offered.
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('names every option, so the pair of fields is not the only thing to go on', () => {
    for (const schema of listSelectableVariantSchemas()) {
      expect(schema.noun.trim().length).toBeGreaterThan(0);
    }
  });

  it('leaves out the types that group products rather than shape them', () => {
    const offered = listSelectableVariantSchemas().map((schema) => schema.key);

    expect(offered).not.toContain('COLLECTION');
    expect(offered).not.toContain('SEASON');
    expect(offered).not.toContain('GENERIC');
    expect(isSelectableVariantType('GENERIC')).toBe(false);
    expect(isSelectableVariantType('CLOTHING')).toBe(true);
  });

  it('still renders a product whose only category is a collection', () => {
    // The schemas stay; only the picker narrows. Otherwise such a product has nothing to render.
    const schema = getProductVariantSchema({ categoryTypes: ['COLLECTION'], variantType: null });

    expect(schema.attributes).toHaveLength(2);
    expect(schema.noun).toBeTruthy();
  });
});
