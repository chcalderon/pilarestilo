import type { ProductDto } from './api';
import {
  buildVariantSchema,
  getSecondaryAttribute,
  summarizeVariantAttributeValues,
} from './variantSchema';

export function summarizeProductVariantSecondaryValues(product: Pick<ProductDto, 'variants' | 'variantFieldConfig'>): string {
  const schema = buildVariantSchema(product.variantFieldConfig ?? null);
  return summarizeVariantAttributeValues(product.variants, schema, getSecondaryAttribute(schema).code);
}
