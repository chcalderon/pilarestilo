import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * There are two doors into an account: the page at /auth/register and the navbar popover. Both
 * create the same account, so both have to say what creating it agrees to — a consent recorded
 * against a text the person was never shown is not informed consent, it is a row in a table.
 *
 * Marketing is asked for apart in both. If it rode along with the terms, all three consents would
 * be worthless as evidence, which is the one thing they exist to be.
 */

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';
const API = `${BASE}/api`;

async function consentsOf(request: APIRequestContext, token: string) {
  const response = await request.get(`${API}/me/privacy/export`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), 'the export is how a customer checks what was recorded').toBeTruthy();
  const body = await response.json();
  return (body.consents ?? []) as Array<{ type: string; revokedAt: string | null }>;
}

async function tokenFromCookie(page: Page) {
  const cookie = (await page.context().cookies()).find(c => c.name === 'pe_token');
  expect(cookie, 'signing up has to leave the session the SSR middleware reads').toBeTruthy();
  return cookie!.value;
}

function uniqueEmail(prefix: string) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}@test.com`;
}

/**
 * Registering is capped at six per minute per IP, and the whole suite shares one IP, so a spec that
 * signs up can be refused by a guardrail that is doing its job. Submitting again after the window
 * keeps that guardrail intact instead of testing around it.
 */
async function submitThroughRateLimit(submit: () => Promise<void>, page: Page, done: () => Promise<boolean>) {
  await submit();
  if (await done()) return;
  const refused = page.getByText(/too many requests|demasiadas solicitudes/i);
  if (await refused.count()) {
    await page.waitForTimeout(61_000);
    await submit();
  }
}

test.describe('Consent at sign-up', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('the popover discloses the terms and asks marketing apart', async ({ page, request }) => {
    test.slow();
    const email = uniqueEmail('pwpop');

    await page.goto(`${BASE}/es/`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Log in' }).click({ timeout: 15000 });
    const panel = page.getByRole('dialog', { name: /crear cuenta o iniciar sesion/i });
    await expect(panel).toBeVisible();
    await panel.getByRole('button', { name: /^registrarse$/i }).first().click();

    // The disclosure and both policies, reachable from the small form as much as the big one.
    await expect(panel.getByRole('link', { name: /términos de compra/i })).toBeVisible();
    await expect(panel.getByRole('link', { name: /política de privacidad/i })).toBeVisible();

    const marketing = panel.getByRole('checkbox');
    await expect(marketing, 'marketing is opt-in, never pre-ticked').not.toBeChecked();

    await panel.locator('input[autocomplete="name"]').fill('Ana Popover');
    await panel.locator('input[type="email"]').fill(email);
    await panel.locator('input[autocomplete="new-password"]').fill('Test12345');
    await marketing.check();
    await submitThroughRateLimit(
      () => panel.getByRole('button', { name: /crear cuenta/i }).click(),
      page,
      () => panel.isHidden(),
    );
    await expect(panel).toBeHidden({ timeout: 20000 });

    const consents = await consentsOf(request, await tokenFromCookie(page));
    const live = consents.filter(c => c.revokedAt == null).map(c => c.type);
    expect(live, 'the terms and the policy come with the account').toEqual(
      expect.arrayContaining(['TERMS', 'PRIVACY']),
    );
    expect(live, 'and marketing because she ticked it, not because she registered').toContain(
      'MARKETING',
    );
  });

  test('leaving the box alone records the account without marketing', async ({ page, request }) => {
    test.slow();
    const email = uniqueEmail('pwpage');

    await page.goto(`${BASE}/es/auth/register`, { waitUntil: 'domcontentloaded' });
    const form = page.locator('main form').first();
    await form.locator('input[autocomplete="name"]').fill('Bea Pagina');
    await form.locator('input[type="email"]').fill(email);
    await form.locator('input[autocomplete="new-password"]').fill('Test12345');
    await submitThroughRateLimit(
      () => form.getByRole('button', { name: /crear cuenta/i }).click(),
      page,
      async () => page.url().includes('/account'),
    );

    /*
     * It navigates to the account on success, which is also the proof the account was created.
     * The wait is generous because registering sends the welcome notification on the request
     * thread: when the provider is slow the response is slow with it, and that is the shop's
     * behaviour rather than the test's.
     */
    await page.waitForURL(/\/account/, { timeout: 40000 });

    const consents = await consentsOf(request, await tokenFromCookie(page));
    const live = consents.filter(c => c.revokedAt == null).map(c => c.type);
    expect(live).toEqual(expect.arrayContaining(['TERMS', 'PRIVACY']));
    expect(live, 'nothing may be inferred from silence').not.toContain('MARKETING');
  });
});
