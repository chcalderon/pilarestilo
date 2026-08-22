import { describe, it, expect } from 'vitest';
import { isExpired, isSpent } from '../DiscountCodeManager';
import type { DiscountCodeDto } from '../../../lib/api';

function code(overrides: Partial<DiscountCodeDto> = {}): DiscountCodeDto {
  return {
    id: '1', code: 'BIENVENIDA-ABC123', type: 'PERCENTAGE', value: 10,
    minOrderAmount: 0, minOrderCurrency: 'CLP',
    validFrom: '2026-01-01', validUntil: '2099-01-01',
    maxUses: 1, timesUsed: 0, active: true,
    ...overrides,
  };
}

describe('isSpent', () => {
  it('is true once every use has been claimed', () => {
    expect(isSpent(code({ maxUses: 1, timesUsed: 1 }))).toBe(true);
  });

  it('is false while a slot remains', () => {
    expect(isSpent(code({ maxUses: 1, timesUsed: 0 }))).toBe(false);
  });
});

describe('isExpired', () => {
  it('treats a fully-redeemed single-use code as no longer vigente, even before its date', () => {
    expect(isExpired(code({ maxUses: 1, timesUsed: 1, validUntil: '2099-01-01' }))).toBe(true);
  });

  it('leaves an unused code within its dates as vigente', () => {
    expect(isExpired(code({ maxUses: 1, timesUsed: 0, validUntil: '2099-01-01' }))).toBe(false);
  });

  it('still treats a past-date code as expired', () => {
    expect(isExpired(code({ maxUses: 5, timesUsed: 0, validUntil: '2000-01-01' }))).toBe(true);
  });
});
