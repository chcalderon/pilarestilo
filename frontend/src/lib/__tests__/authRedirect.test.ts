import { describe, expect, it } from 'vitest';
import { resolveAuthLocale } from '../authRedirect';

describe('resolveAuthLocale', () => {
  it('matches the login page for each supported locale', () => {
    expect(resolveAuthLocale('/es/auth/login')).toBe('es');
    expect(resolveAuthLocale('/en/auth/login')).toBe('en');
  });

  it('matches the register page for each supported locale', () => {
    expect(resolveAuthLocale('/es/auth/register')).toBe('es');
    expect(resolveAuthLocale('/en/auth/register')).toBe('en');
  });

  it('ignores everything else', () => {
    for (const path of ['/es/', '/es/checkout', '/admin/login', '/es/account', '/es/auth']) {
      expect(resolveAuthLocale(path)).toBeNull();
    }
  });
});
