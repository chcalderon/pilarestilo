import type { ProductVariantDto } from './api';

const BASE_SIZE_ORDER = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'];
const BASE_SIZE_RANK = new Map(BASE_SIZE_ORDER.map((size, index) => [size, index]));

function sizeKeyParts(size: string): number[] {
  const tokens = size.split('-').filter(Boolean);
  if (tokens.length === 0) return [999];
  return tokens.map((token) => BASE_SIZE_RANK.get(token) ?? 999);
}

function compareSizeLabels(a: string, b: string): number {
  const aParts = sizeKeyParts(a);
  const bParts = sizeKeyParts(b);
  const max = Math.max(aParts.length, bParts.length);
  for (let i = 0; i < max; i += 1) {
    const diff = (aParts[i] ?? 999) - (bParts[i] ?? 999);
    if (diff !== 0) return diff;
  }
  if (aParts.length !== bParts.length) return aParts.length - bParts.length;
  return a.localeCompare(b);
}

export function summarizeVariantSizes(variants?: ProductVariantDto[]): string {
  if (!Array.isArray(variants) || variants.length === 0) return '';
  const uniqueSizes = Array.from(
    new Set(
      variants
        .map((variant) => variant.size?.trim().toUpperCase().replace(/\s+/g, ''))
        .filter(Boolean),
    ),
  );
  if (uniqueSizes.length === 0) return '';
  uniqueSizes.sort(compareSizeLabels);
  return uniqueSizes.join('-');
}
