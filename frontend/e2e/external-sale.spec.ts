import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * Registering an off-platform sale (Fase 2 F). The admin form creates a real paid order that
 * shows up in /admin/ventas flagged "Sin boleta" and has decremented the product's stock.
 */

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';
const API = `${BASE}/api`;
const ADMIN_EMAIL = process.env.TEST_ADMIN_EMAIL ?? 'admin@pilarestilo.com';
const ADMIN_PASSWORD = process.env.TEST_ADMIN_PASSWORD ?? 'admin2026';

async function adminToken(request: APIRequestContext) {
  const response = await request.post(`${API}/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  return (await response.json()).accessToken as string;
}

async function loginAsAdmin(page: Page) {
  await page.goto(`${BASE}/admin/login`, { waitUntil: 'domcontentloaded' });
  const form = page.locator('#login-form-mount form').first();
  await form.locator('input[autocomplete="email"]').fill(ADMIN_EMAIL);
  await form.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD);
  await form.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin(?!\/login)/, { timeout: 20000 });
}

/** A product with an in-stock variant to sell. Returns { name, color, size, stockBefore }. */
async function anInStockVariant(request: APIRequestContext) {
  const res = await request.get(`${API}/products?page=0&size=50&active=true&inStock=true`);
  const body = await res.json();
  for (const p of body.content ?? []) {
    const v = (p.variants ?? []).find((variant: { stockAvailable: number }) => variant.stockAvailable >= 1);
    if (v) return { id: p.id as string, name: p.name as string, color: v.color as string, size: v.size as string, stockBefore: v.stockAvailable as number };
  }
  return null;
}

async function variantStock(request: APIRequestContext, productId: string, color: string, size: string) {
  const res = await request.get(`${API}/products/${productId}`);
  const body = await res.json();
  const v = (body.variants ?? []).find((variant: { color: string; size: string }) => variant.color === color && variant.size === size);
  return v?.stockAvailable ?? null;
}

test.describe('Registrar venta', () => {
  test.use({ viewport: { width: 1440, height: 950 } });

  test('a social sale becomes a paid order and drops the stock', async ({ page, request }) => {
    test.slow();
    const token = await adminToken(request);
    const variant = await anInStockVariant(request);
    test.skip(!variant, 'no hay un producto con stock de variante para probar');
    if (!variant) return;

    await loginAsAdmin(page);
    await page.goto(`${BASE}/admin/ventas`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /registrar venta/i }).click();

    const drawer = page.getByRole('dialog', { name: /registrar venta/i });
    await expect(drawer).toBeVisible({ timeout: 20000 });

    await drawer.getByPlaceholder(/buscar producto/i).fill(variant.name.slice(0, 6));
    await drawer.getByRole('button', { name: new RegExp(variant.name.slice(0, 6), 'i') }).first().click();

    // Pick the exact in-stock variant we resolved above.
    const colorSelect = drawer.getByLabel('Color', { exact: true });
    if (await colorSelect.count()) {
      await colorSelect.selectOption(variant.color);
      await drawer.getByLabel('Talla', { exact: true }).selectOption(variant.size);
    }

    await drawer.getByLabel(/^comprador$/i).fill('Cliente E2E');
    await drawer.getByLabel(/contacto/i).fill('@cliente_e2e');
    await drawer.getByRole('button', { name: /retiro en persona/i }).click();
    await drawer.getByRole('button', { name: /^registrar venta$/i }).click();

    await expect(drawer).toBeHidden({ timeout: 20000 });

    // The new sale is in the list, flagged "Sin boleta".
    await expect(page.getByText('Cliente E2E').first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/sin boleta/i).first()).toBeVisible();

    // Stock dropped by 1.
    const after = await variantStock(request, variant.id, variant.color, variant.size);
    expect(after).toBe(variant.stockBefore - 1);
  });
});
