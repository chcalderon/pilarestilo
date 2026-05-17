import type { CategoryType, ProductDto, ProductVariantDto } from './api';
import {
  getProductVariantSchema,
  getSecondaryAttribute,
  summarizeVariantAttributeValues,
} from './variantSchema';

export function summarizeVariantSecondaryValues(
  variants: ProductVariantDto[] | undefined,
  categoryTypes?: CategoryType[],
): string {
  const schema = getProductVariantSchema({ categoryTypes });
  return summarizeVariantAttributeValues(variants, schema, getSecondaryAttribute(schema).code);
}

export function summarizeProductVariantSecondaryValues(product: Pick<ProductDto, 'variants' | 'categoryTypes'>): string {
  const schema = getProductVariantSchema(product);
  return summarizeVariantAttributeValues(product.variants, schema, getSecondaryAttribute(schema).code);
}
