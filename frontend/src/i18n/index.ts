import esTranslations from './es.json';
import enTranslations from './en.json';

export const SUPPORTED_LOCALES = ['es', 'en'] as const;
export type Locale = typeof SUPPORTED_LOCALES[number];
export const DEFAULT_LOCALE: Locale = 'es';

type TranslationDict = Record<string, unknown>;

const translations: Record<Locale, TranslationDict> = {
  es: esTranslations as unknown as TranslationDict,
  en: enTranslations as unknown as TranslationDict,
};

/**
 * Get a translated string by dot-notation key, e.g. t('nav.home', 'es')
 */
export function t(key: string, locale: Locale): string {
  const dict = translations[locale] ?? translations[DEFAULT_LOCALE];
  const parts = key.split('.');
  let current: unknown = dict;
  for (const part of parts) {
    if (current === null || typeof current !== 'object') {
      break;
    }
    current = (current as Record<string, unknown>)[part];
  }
  if (typeof current === 'string') return current;
  // Fallback to default locale
  if (locale !== DEFAULT_LOCALE) return t(key, DEFAULT_LOCALE);
  return key;
}

/**
 * Extract locale from a URL pathname, e.g. /es/products → 'es'
 */
export function getLocaleFromUrl(url: URL): Locale {
  const [, first] = url.pathname.split('/');
  if ((SUPPORTED_LOCALES as readonly string[]).includes(first)) {
    return first as Locale;
  }
  return DEFAULT_LOCALE;
}
