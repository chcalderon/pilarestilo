/**
 * Post-login redirects come from a query parameter, so they are attacker-controlled.
 * Sending the browser to an unvalidated value turns our own login page into a phishing
 * launcher: the victim authenticates on the real site and lands wherever the link author
 * chose. Only same-origin paths are allowed through.
 */

const BACKSLASH = 92;
const SPACE = 32;

/**
 * Browsers strip control characters and whitespace, and treat a backslash as a path
 * separator, before resolving a URL. Left in, such a value can pass a naive prefix check
 * and still navigate off-site: both a backslash after the leading slash and a tab injected
 * before the host end up on another origin.
 */
function hasCharacterBrowsersRewrite(value: string): boolean {
  for (let i = 0; i < value.length; i += 1) {
    const code = value.codePointAt(i)!;
    if (code <= SPACE || code === BACKSLASH) return true;
  }
  return false;
}

export function isSafeRedirectPath(raw: unknown): raw is string {
  if (typeof raw !== 'string' || raw.length === 0) return false;
  if (hasCharacterBrowsersRewrite(raw)) return false;

  // Must be a rooted path. Anything else is absolute, or relative to the current page.
  if (!raw.startsWith('/')) return false;

  // `//host` is protocol-relative: same-origin in shape, cross-origin in effect.
  if (raw.startsWith('//')) return false;

  return true;
}

/**
 * Returns `raw` when it is a safe same-origin path, otherwise `fallback`.
 * Call this where the value enters the app, not at the navigation site, so that no
 * component ever holds an unvalidated destination.
 */
export function safeRedirectPath(raw: unknown, fallback: string): string {
  return isSafeRedirectPath(raw) ? raw : fallback;
}
