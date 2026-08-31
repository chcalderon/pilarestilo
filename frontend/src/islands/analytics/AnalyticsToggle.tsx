import { useEffect, useState } from 'react';
import { isAnalyticsOptedOut, setAnalyticsOptOut } from '../../lib/analytics';
import type { Locale } from '../../i18n/index';

interface Props {
  readonly locale: Locale;
}

const copy = {
  es: {
    label: 'Analítica de navegación',
    on: 'Activada',
    off: 'Desactivada',
    onHelp:
      'Se registra qué páginas se ven y en qué paso se abandona la compra, para mejorar la tienda. Sin cookies: un identificador temporal que se borra al cerrar la pestaña.',
    offHelp: 'Desactivada en este navegador. No se registra tu navegación.',
    switchLabel: 'Activar o desactivar la analítica de navegación',
  },
  en: {
    label: 'Browsing analytics',
    on: 'On',
    off: 'Off',
    onHelp:
      'We record which pages are viewed and where checkout is abandoned, to improve the store. No cookies: a temporary id that is cleared when you close the tab.',
    offHelp: 'Off in this browser. Your browsing is not recorded.',
    switchLabel: 'Turn browsing analytics on or off',
  },
} as const;

/**
 * The opt-out control the privacy policy points at. Analytics is on by default (cookieless, no
 * stored profile for anonymous visitors); this flips the `pe-analytics-opt-out` flag that both
 * the wrapper and the PostHog snippet honour. Per browser, not per account — it writes only to
 * this device's localStorage.
 */
export default function AnalyticsToggle({ locale }: Props) {
  const t = copy[locale === 'es' ? 'es' : 'en'];
  const [ready, setReady] = useState(false);
  const [enabled, setEnabled] = useState(true);

  useEffect(() => {
    setEnabled(!isAnalyticsOptedOut());
    setReady(true);
  }, []);

  const onToggle = (next: boolean) => {
    setEnabled(next);
    setAnalyticsOptOut(!next);
  };

  let stateWord = '…';
  if (ready) stateWord = enabled ? t.on : t.off;

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between gap-6">
        <span className="font-sans text-sm font-medium text-pe-black">{t.label}</span>
        <label className="inline-flex items-center gap-3 cursor-pointer shrink-0">
          <span className="font-sans text-[0.68rem] uppercase tracking-wider text-pe-muted">
            {stateWord}
          </span>
          <span className="relative inline-flex h-5 w-9 items-center">
            <input
              type="checkbox"
              className="peer sr-only"
              checked={enabled}
              disabled={!ready}
              onChange={(e) => onToggle(e.target.checked)}
              aria-label={t.switchLabel}
            />
            <span className="absolute inset-0 rounded-full bg-pe-black/15 peer-checked:bg-pe-rose-action transition-colors" />
            <span className="absolute left-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform peer-checked:translate-x-4" />
          </span>
        </label>
      </div>
      <p className="mt-3 font-sans text-sm text-pe-charcoal leading-relaxed">
        {ready && !enabled ? t.offHelp : t.onHelp}
      </p>
    </div>
  );
}
