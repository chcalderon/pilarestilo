import { SUPPORTED_LOCALES, type Locale } from '../i18n';

const AUTH_PAGES = ['login', 'register'] as const;

/**
 * Returns the locale when `pathname` is that locale's storefront login or register page,
 * otherwise null. Used to redirect an already-authenticated visitor away from a form they
 * have no reason to see, the same way the checkout guard redirects an unauthenticated one in.
 */
export function resolveAuthLocale(pathname: string): Locale | null {
  for (const locale of SUPPORTED_LOCALES) {
    for (const page of AUTH_PAGES) {
      if (pathname === `/${locale}/auth/${page}`) {
        return locale;
      }
    }
  }
  return null;
}
