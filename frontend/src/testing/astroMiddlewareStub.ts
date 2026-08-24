/**
 * `astro:middleware` is a virtual module Astro's own Vite plugin provides at build time; Vitest
 * never loads that plugin, so it cannot resolve the bare specifier even under `vi.mock`, which
 * only intercepts modules Vite can already resolve. Aliased in for `middleware.ts` alone --
 * `defineMiddleware` is a type-only identity helper at runtime, so a real implementation isn't
 * needed to test the handler it wraps.
 */
export function defineMiddleware<T>(fn: T): T {
  return fn;
}
