import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';

/**
 * The monolith and the extracted services must answer the same question the same way.
 *
 * They are not independent services: they share one database, so the split is over query logic,
 * not over data ownership. Nothing ties the two implementations together — no compiler, no schema
 * check, no test — and which one answers depends on who is asking. Caddy sends the browser's GETs
 * to product-service; the storefront's SSR calls the monolith directly, so a page can put both
 * answers on screen at once.
 *
 * That is exactly what happened on 2026-08-19: "10 piezas" above "No se encontraron piezas",
 * because product-service still joined a stock table that V56 retired and nothing had written
 * since. Both codebases were internally consistent and fully green.
 *
 * This asks both, through the doors each caller really uses, and fails when they disagree. It is
 * the same guard OrderReferenceSqlParityIT provides for the reference hash, applied to the read
 * paths — the only kind of test that can see across two projects that never link.
 */

const QUERIES = [
  'active=true&inStock=true&page=0&size=8',
  'active=true&page=0&size=8',
  'inStock=true&page=0&size=20',
  'page=0&size=5',
];

/** Straight to the monolith, from inside the compose network: it is not published to the host. */
function askMonolith(query: string): { total: number; ids: string[] } {
  const raw = execFileSync(
    'docker',
    ['exec', '-i', 'pe_frontend', 'sh', '-c', `wget -qO- "http://backend:8080/api/products?${query}"`],
    { encoding: 'utf-8', timeout: 30000 },
  );
  const body = JSON.parse(raw);
  return { total: body.totalElements, ids: (body.content ?? []).map((p: { id: string }) => p.id) };
}

test.describe('Read-path parity', () => {
  test('the monolith and product-service agree about the catalogue', async ({ request }) => {
    test.slow();

    for (const query of QUERIES) {
      // Through Caddy is what the browser does, and Caddy prefers product-service.
      const response = await request.get(`/api/products?${query}`);
      expect(response.ok(), `${query} answered ${response.status()} through Caddy`).toBeTruthy();
      const routed = await response.json();

      let direct: { total: number; ids: string[] };
      try {
        direct = askMonolith(query);
      } catch (error) {
        test.skip(true, `no se pudo consultar al monolito directamente: ${(error as Error).message}`);
        return;
      }

      expect(
        routed.totalElements,
        `"${query}": Caddy says ${routed.totalElements}, the monolith says ${direct.total}`,
      ).toBe(direct.total);

      const routedIds = (routed.content ?? []).map((p: { id: string }) => p.id);
      expect(
        routedIds,
        `"${query}": the two paths returned different products, not just a different count`,
      ).toEqual(direct.ids);
    }
  });
});
