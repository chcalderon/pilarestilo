import { test, expect, type Page } from '@playwright/test';

/**
 * Regression guard for the storefront → /admin bounce.
 *
 * The navbar login popover used to persist the token only to Zustand/localStorage and never
 * write the `pe_token` cookie. The SSR middleware reads nothing but that cookie, so an admin
 * who logged in through the popover saw "Panel admin" and was then redirected to /admin/login.
 * The cookie is now written by the auth store itself.
 */

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';
const ADMIN_EMAIL = process.env.TEST_ADMIN_EMAIL ?? 'admin@pilarestilo.com';
const ADMIN_PASSWORD = process.env.TEST_ADMIN_PASSWORD ?? 'admin2026';

function popover(page: Page) {
  return page.getByRole('dialog', { name: /crear cuenta o iniciar sesion/i });
}

async function loginThroughNavbarPopover(page: Page) {
  await page.goto(`${BASE}/es/`, { waitUntil: 'domcontentloaded' });

  // AccountMenu renders a skeleton until its mount-time token validation settles.
  const trigger = page.getByRole('button', { name: 'Log in' });
  await trigger.click({ timeout: 15000 });

  const panel = popover(page);
  await expect(panel).toBeVisible();
  await panel.getByRole('button', { name: /iniciar sesion/i }).first().click();

  await panel.locator('input[type="email"]').fill(ADMIN_EMAIL);
  await panel.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD);
  await panel.getByRole('button', { name: /iniciar sesion/i }).last().click();

  // The popover closes on success and leaves the user on the storefront (no navigation).
  await expect(panel).toBeHidden({ timeout: 15000 });
}

/** The account dropdown trigger — its label is the user's first name, so match structurally. */
function accountMenuTrigger(page: Page) {
  return page.locator('header button[aria-haspopup="true"]').first();
}

async function readAuthCookie(page: Page) {
  const cookies = await page.context().cookies();
  return cookies.find(c => c.name === 'pe_token');
}

test.describe('Admin panel access from the storefront', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test('popover login writes the pe_token cookie the SSR middleware reads', async ({ page }) => {
    test.slow();
    await loginThroughNavbarPopover(page);

    const cookie = await readAuthCookie(page);
    expect(cookie, 'popover login must mirror the token into pe_token').toBeTruthy();
    expect(cookie!.value.split('.')).toHaveLength(3);
    // max-age derives from the JWT exp (24h access tokens), not a hardcoded constant.
    expect(cookie!.expires).toBeGreaterThan(Date.now() / 1000);
  });

  test('"Panel admin" reaches the dashboard instead of bouncing to /admin/login', async ({ page }) => {
    test.slow();
    await loginThroughNavbarPopover(page);

    await accountMenuTrigger(page).click();
    const panelLink = page.getByRole('link', { name: /panel admin/i });
    await expect(panelLink).toBeVisible();

    await panelLink.click();
    await page.waitForURL(/\/admin\/?$/, { timeout: 20000 });
    expect(page.url()).not.toContain('/admin/login');
  });

  test('logout clears both the cookie and the persisted store', async ({ page }) => {
    test.slow();
    await loginThroughNavbarPopover(page);

    await accountMenuTrigger(page).click();
    await page.getByRole('button', { name: /cerrar sesion/i }).click();
    await page.waitForURL(/\/es\/?$/, { timeout: 15000 });

    expect(await readAuthCookie(page)).toBeUndefined();
    const stored = await page.evaluate(() => window.localStorage.getItem('pe-auth'));
    expect(stored ? JSON.parse(stored).state.token : null).toBeNull();
  });

  test('a deleted cookie is repaired from the store on the next page load', async ({ page }) => {
    test.slow();
    await loginThroughNavbarPopover(page);

    await page.context().clearCookies({ name: 'pe_token' });
    expect(await readAuthCookie(page)).toBeUndefined();

    await page.goto(`${BASE}/es/`, { waitUntil: 'domcontentloaded' });
    await expect.poll(async () => (await readAuthCookie(page))?.name, { timeout: 15000 })
      .toBe('pe_token');
  });
});
