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
} from '../variantSchema';
import type { VariantFieldConfigDto } from '../api';

/**
 * Characterization tests for the multi-value/custom-value composition logic -- unchanged by the
 * category-to-template decoupling, only the type of buildVariantSchema's parameter changed.
 */
const CLOTHING_CONFIG: VariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};

describe('variantSchema: multi-value composition (must survive the template-driven rewrite)', () => {
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
