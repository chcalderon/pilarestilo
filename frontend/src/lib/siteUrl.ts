/**
 * The site's public origin — `https://pilarestilo.com` in production — used for canonical links,
 * `og:url`, hreflang alternates and the sitemap.
 *
 * Resolution order:
 *  1. `PUBLIC_SITE_URL` from the SSR container env (not baked at build, matching the
 *     `INTERNAL_API_BASE_URL` pattern in `api.ts`). Set this when several hostnames serve the site
 *     and one has to win.
 *  2. The `X-Forwarded-Host` / `X-Forwarded-Proto` headers Caddy sets, so the value is correct
 *     behind the proxy without any configuration.
 *  3. The request URL's own origin.
 *  4. `http://localhost:4321` for calls with no request context.
 */
const RUNTIME_ENV: Record<string, string | undefined> | undefined =
  (globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  }).process?.env;

const CONFIGURED_SITE_URL: string | undefined =
  RUNTIME_ENV?.PUBLIC_SITE_URL ?? import.meta.env.PUBLIC_SITE_URL;

/** Trims one or more trailing slashes without a backtracking regex (Sonar S8786). */
function stripTrailingSlashes(value: string): string {
  let end = value.length;
  while (end > 0 && value[end - 1] === '/') end -= 1;
  return value.slice(0, end);
}

/** Origin with no trailing slash, e.g. `https://pilarestilo.com`. */
export function resolveSiteUrl(requestUrl?: URL | string, headers?: Headers): string {
  if (CONFIGURED_SITE_URL) return stripTrailingSlashes(CONFIGURED_SITE_URL);

  if (headers) {
    const forwardedHost = headers.get('x-forwarded-host') ?? headers.get('host');
    if (forwardedHost) {
      const host = forwardedHost.split(',')[0].trim();
      const proto =
        headers.get('x-forwarded-proto')?.split(',')[0].trim() ??
        (requestUrl ? new URL(requestUrl).protocol.replace(':', '') : 'https');
      return `${proto}://${host}`;
    }
  }

  if (requestUrl) return new URL(requestUrl).origin;
  return 'http://localhost:4321';
}

/** Absolute URL for a site-relative path or a value that is already absolute. */
export function absoluteUrl(pathOrUrl: string, requestUrl?: URL | string, headers?: Headers): string {
  if (/^https?:\/\//i.test(pathOrUrl)) return pathOrUrl;
  const base = resolveSiteUrl(requestUrl, headers);
  return `${base}${pathOrUrl.startsWith('/') ? '' : '/'}${pathOrUrl}`;
}

/**
 * The canonical URL for the current request: the origin plus the path with the query string
 * dropped and the trailing slash removed — except for the site root and a bare locale root
 * (`/es`, `/en`), which are served, linked, and reached by the `/` redirect *with* a slash, so
 * the canonical has to match that exact URL or Google reports the pair as duplicates. Same value
 * BaseLayout emits in `<link rel="canonical">`.
 */
export function canonicalUrlFor(requestUrl: URL, headers?: Headers): string {
  const stripped = stripTrailingSlashes(requestUrl.pathname);
  const isLocaleRoot = /^\/[a-z]{2}$/.test(stripped);
  const path = stripped.length > 1 ? (isLocaleRoot ? `${stripped}/` : stripped) : '/';
  return `${resolveSiteUrl(requestUrl, headers)}${path}`;
}
