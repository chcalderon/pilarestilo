import { describe, expect, it } from 'vitest';
import { isCategoryCompatibleWith, getProductVariantSchema } from '../variantSchema';

/**
 * The catalogue has two kinds of category. "Mujer" and "Verano" say how a product is grouped;
 * "Zapatos" and "Vestidos" say what it is. Only the second kind constrains the variants, and a
 * product is only ever one of them.
 */
describe('isCategoryCompatibleWith', () => {
  it('lets a shape category through when it matches', () => {
    expect(isCategoryCompatibleWith('CLOTHING', 'CLOTHING')).toBe(true);
    expect(isCategoryCompatibleWith('SHOES', 'SHOES')).toBe(true);
  });

  /** A product cannot be both a shoe and a dress, so its categories cannot say both. */
  it('refuses a shape category that does not match', () => {
    expect(isCategoryCompatibleWith('SHOES', 'CLOTHING')).toBe(false);
    expect(isCategoryCompatibleWith('JEWELRY', 'SHOES')).toBe(false);
    expect(isCategoryCompatibleWith('CLOTHING', 'ACCESSORY')).toBe(false);
  });

  /** A dress belongs to "Mujer" and to "Verano" as naturally as it belongs to "Vestidos". */
  it('always allows grouping categories', () => {
    for (const variantType of ['CLOTHING', 'SHOES', 'JEWELRY', 'ACCESSORY'] as const) {
      expect(isCategoryCompatibleWith('GENERIC', variantType)).toBe(true);
      expect(isCategoryCompatibleWith('SEASON', variantType)).toBe(true);
      expect(isCategoryCompatibleWith('COLLECTION', variantType)).toBe(true);
    }
  });

  /** Refusing an unclassified category would lock the admin out of it for no stated reason. */
  it('allows a category with no type stated', () => {
    expect(isCategoryCompatibleWith(null, 'SHOES')).toBe(true);
    expect(isCategoryCompatibleWith(undefined, 'CLOTHING')).toBe(true);
  });
});

describe('getProductVariantSchema', () => {
  it('honours a stated type over the categories', () => {
    const schema = getProductVariantSchema({
      categoryTypes: ['CLOTHING'],
      variantType: 'SHOES',
    });

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
