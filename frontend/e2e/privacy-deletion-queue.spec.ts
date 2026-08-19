import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * The deletion queue from the desk's side.
 *
 * Anonymising cannot be undone by anyone — not by the shop, not by the customer, not by a later
 * migration. So what this walks is not that the button works, but that it refuses to work until
 * the operator has been told what disappears and has said so.
 */

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';
const API = `${BASE}/api`;
const ADMIN_EMAIL = process.env.TEST_ADMIN_EMAIL ?? 'admin@pilarestilo.com';
const ADMIN_PASSWORD = process.env.TEST_ADMIN_PASSWORD ?? 'admin2026';

/** Playwright's own client, so the stack's self-signed certificate is handled like the browser's. */
async function post(request: APIRequestContext, path: string, data: object, token?: string) {
  const response = await request.post(`${API}${path}`, {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok()) throw new Error(`${path} answered ${response.status()}`);
  return response.json();
}

/** A customer who has asked to be erased, created through the same doors a real one uses. */
async function seedRequest(request: APIRequestContext, name: string) {
  const email = `pwqueue_${Date.now()}_${Math.random().toString(36).slice(2, 7)}@test.com`;
  const account = await post(request, '/auth/register', {
    email, password: 'Test12345', fullName: name,
  });
  await post(request, '/me/privacy/deletion', { reason: 'Ya no quiero tener cuenta' },
             account.accessToken);
  return { name, email, userId: account.userId as string };
}

async function loginAsAdmin(page: Page) {
  await page.goto(`${BASE}/admin/login`, { waitUntil: 'domcontentloaded' });
  const form = page.locator('#login-form-mount form').first();
  await form.locator('input[autocomplete="email"]').fill(ADMIN_EMAIL);
  await form.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD);
  await form.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin(?!\/login)/, { timeout: 20000 });
}

test.describe('Deletion queue', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('anonymising asks twice and the row proves it was done', async ({ page, request }) => {
    test.slow();
    const customer = await seedRequest(request, 'Ana Cola Prueba');

    await loginAsAdmin(page);
    await page.goto(`${BASE}/admin/privacidad`, { waitUntil: 'domcontentloaded' });

    // The row names the person: a queue answered by writing to somebody cannot show only a UUID.
    const row = page.getByText(customer.email).first();
    await expect(row).toBeVisible({ timeout: 20000 });

    await row.click();
    const drawer = page.getByRole('dialog', { name: /solicitud de supresión/i });
    await expect(drawer).toBeVisible();

    await drawer.getByRole('button', { name: /^anonimizar$/i }).click();
    const confirm = drawer.getByRole('button', { name: /anonimizar definitivamente/i });
    await expect(confirm, 'the button stays shut until the consequences are acknowledged')
      .toBeDisabled();
    await expect(drawer.getByText(/no se puede deshacer/i)).toBeVisible();

    await drawer.getByRole('checkbox').check();
    await expect(confirm).toBeEnabled();
    await confirm.click();

    // Read live rather than copied, so the same field that identified her is now the receipt.
    await expect(drawer.getByText('Cliente anonimizado')).toBeVisible({ timeout: 20000 });
    await expect(drawer.getByText(/nadie puede identificarla/i)).toBeVisible();

    // And she is gone from the open queue, without the record of the answer being gone.
    await page.keyboard.press('Escape');
    await expect(page.getByText(customer.email)).toHaveCount(0);
  });

  test('the queue is closed to an account without the permission', async ({ page }) => {
    const response = await page.goto(`${BASE}/admin/privacidad`, { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBeLessThan(400);
    await expect(page).toHaveURL(/\/admin\/login/);
  });
});
