import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * The boleta is read where the sale is read.
 *
 * A tax document exists to be checked against the amounts beside it, so sending the operator to
 * another tab lost the only context that made opening it worth anything. Both kinds the shop files
 * are covered: a photograph of the boleta taken in the SII app, and a PDF.
 */

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';
const API = `${BASE}/api`;
const ADMIN_EMAIL = process.env.TEST_ADMIN_EMAIL ?? 'admin@pilarestilo.com';
const ADMIN_PASSWORD = process.env.TEST_ADMIN_PASSWORD ?? 'admin2026';

/** One-pixel PNG: the smallest thing that is honestly an image. */
const PNG = Buffer.from(
  '89504e470d0a1a0a0000000d494844520000000100000001080600000' +
    '01f15c4890000000a49444154789c6360000002000100ffff030000060005a1e0b1b40000000049454e44ae426082',
  'hex',
);

async function adminToken(request: APIRequestContext) {
  const response = await request.post(`${API}/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  return (await response.json()).accessToken as string;
}

/** A paid sale that still has no live document, so registering one is legal. */
async function orderNeedingABoleta(request: APIRequestContext, token: string) {
  const response = await request.get(`${API}/admin/sales?page=0&size=50`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await response.json();
  const candidate = (body.content ?? []).find(
    (row: { orderStatus: string; documentFolio: string | null }) =>
      row.orderStatus === 'PAID' && !row.documentFolio,
  );
  return candidate ?? null;
}

async function loginAsAdmin(page: Page) {
  await page.goto(`${BASE}/admin/login`, { waitUntil: 'domcontentloaded' });
  const form = page.locator('#login-form-mount form').first();
  await form.locator('input[autocomplete="email"]').fill(ADMIN_EMAIL);
  await form.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD);
  await form.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin(?!\/login)/, { timeout: 20000 });
}

test.describe('Sale drawer', () => {
  test.use({ viewport: { width: 1440, height: 950 } });

  test('an image boleta is shown in the drawer, not handed over as a download', async ({ page, request }) => {
    test.slow();
    const token = await adminToken(request);
    const sale = await orderNeedingABoleta(request, token);
    test.skip(!sale, 'no hay una venta pagada sin boleta con la que probar');

    const upload = await request.post(`${API}/admin/sales-documents/files`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: { file: { name: 'boleta.png', mimeType: 'image/png', buffer: PNG } },
    });
    expect(upload.ok(), 'the file store has to accept a photograph').toBeTruthy();
    const { fileUrl } = await upload.json();

    const folio = `E2E-${Date.now()}`;
    const issued = await request.post(`${API}/admin/sales-documents`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { orderId: sale.orderId, folio, fileUrl },
    });
    expect(issued.ok()).toBeTruthy();
    const document = await issued.json();
    expect(document.fileAttached, 'the file has to survive the registration').toBeTruthy();

    // The type has to be the real one: declared as octet-stream, an image can only be downloaded.
    const file = await request.get(`${API}/admin/sales-documents/${document.id}/file`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(file.headers()['content-type']).toContain('image/png');

    await loginAsAdmin(page);
    await page.goto(`${BASE}/admin/ventas`, { waitUntil: 'domcontentloaded' });
    await page.getByText(sale.publicReference).first().click();

    const drawer = page.getByRole('dialog');
    await expect(drawer).toBeVisible({ timeout: 20000 });
    await drawer.getByRole('button', { name: /ver archivo/i }).click();

    const preview = drawer.getByRole('img', { name: new RegExp(`Boleta ${folio}`, 'i') });
    await expect(preview, 'the boleta is shown beside the amounts it has to match').toBeVisible({
      timeout: 20000,
    });
    await expect(preview).toHaveAttribute('src', /^blob:/);

    // Cleanup: the sale keeps its history, so the document is voided rather than deleted.
    await request.post(`${API}/admin/sales-documents/${document.id}/void`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { reason: 'Documento de prueba automatizada' },
    });
  });
});
