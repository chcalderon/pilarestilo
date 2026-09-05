import { describe, expect, it } from 'vitest';
import {
  santiagoWallTimeToInstant,
  instantToSantiagoInputValue,
  instantToSantiagoLabel,
} from '../santiagoTime';

describe('santiagoTime', () => {
  it('maps a summer (CLST, UTC-3) wall time to the right UTC instant', () => {
    // Chile summer: February. UTC-3, so 10:00 Santiago = 13:00 UTC.
    expect(santiagoWallTimeToInstant('2026-02-15T10:00')).toBe('2026-02-15T13:00:00.000Z');
  });

  it('maps a winter (CLT, UTC-4) wall time to the right UTC instant', () => {
    // Chile winter: June. UTC-4, so 10:00 Santiago = 14:00 UTC.
    expect(santiagoWallTimeToInstant('2026-06-15T10:00')).toBe('2026-06-15T14:00:00.000Z');
  });

  it('round-trips through the input-value formatter', () => {
    const iso = santiagoWallTimeToInstant('2026-06-15T10:00');
    expect(instantToSantiagoInputValue(iso)).toBe('2026-06-15T10:00');
  });

  it('formats a readable Santiago label', () => {
    const label = instantToSantiagoLabel('2026-06-15T14:00:00.000Z');
    expect(label).toMatch(/10:00/);
  });
});
