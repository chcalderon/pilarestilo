/**
 * Thin wrapper over the PostHog snippet loaded in BaseLayout.
 *
 * The snippet (not the npm bundle) keeps ~50 KB off every page's critical path — `array.js` loads
 * async from the same-origin `/phog` reverse proxy, so ad-blockers that filter `*.posthog.com`
 * don't drop the funnel events. This module gives the islands a typed, guarded API and never
 * throws when PostHog is absent (key unset, still loading, opted out, blocked anyway).
 *
 * Privacy (Ley 21.719): configured cookieless in the snippet — `persistence: 'sessionStorage'`
 * (anonymous id lives for the visit only), `person_profiles: 'identified_only'` (an anonymous
 * visitor is a bare funnel count, no stored profile), Do Not Track honoured, session recording
 * off. An opt-out flag in localStorage disables capture entirely. The privacy policy discloses it.
 */

export type CommerceEvent =
  | 'product_viewed'
  | 'product_list_viewed'
  | 'add_to_cart'
  | 'remove_from_cart'
  | 'cart_viewed'
  | 'checkout_started'
  | 'checkout_step'
  | 'payment_method_selected';

interface PostHogLike {
  capture: (event: string, properties?: Record<string, unknown>) => void;
  identify: (distinctId: string, properties?: Record<string, unknown>) => void;
  reset: () => void;
  opt_in_capturing: () => void;
  opt_out_capturing: () => void;
}

const OPT_OUT_KEY = 'pe-analytics-opt-out';

function ph(): PostHogLike | null {
  if (typeof window === 'undefined') return null;
  const instance = (window as typeof window & { posthog?: PostHogLike }).posthog;
  // The snippet stub queues calls before array.js loads; `capture` only exists once it has.
  return instance && typeof instance.capture === 'function' ? instance : null;
}

function readOptOut(): boolean {
  try {
    return window.localStorage.getItem(OPT_OUT_KEY) === '1';
  } catch {
    return false;
  }
}

/** Fire a commerce event. No-ops when PostHog is unavailable or capture is off. */
export function track(event: CommerceEvent, properties?: Record<string, unknown>): void {
  ph()?.capture(event, properties);
}

/** Tie the anonymous journey so far to a known customer — call right after login. */
export function identifyCustomer(customerId: string): void {
  if (customerId) ph()?.identify(customerId);
}

/** Drop the identity on logout so the next person on this browser starts clean. */
export function resetAnalytics(): void {
  ph()?.reset();
}

/** Read whether this browser has opted out of analytics. */
export function isAnalyticsOptedOut(): boolean {
  return typeof window !== 'undefined' && readOptOut();
}

/** Turn analytics capture on/off for this browser. A "manage analytics" toggle wires here. */
export function setAnalyticsOptOut(optOut: boolean): void {
  try {
    if (optOut) window.localStorage.setItem(OPT_OUT_KEY, '1');
    else window.localStorage.removeItem(OPT_OUT_KEY);
  } catch {
    /* storage unavailable — the snippet re-reads the flag on next load anyway */
  }
  if (optOut) ph()?.opt_out_capturing();
  else ph()?.opt_in_capturing();
}
