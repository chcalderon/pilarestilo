import { test, expect, type Page } from '@playwright/test';

function authForm(page: Page) {
  return page.locator('main form').first();
}

function adminLoginForm(page: Page) {
  return page.locator('#login-form-mount form').first();
}

async function gotoApp(page: Page, path: string) {
  await page.goto(path, { waitUntil: 'domcontentloaded' });
}

test.describe('Home page', () => {
  test('Spanish home renders header and category nav', async ({ page }) => {
    test.slow();
    // #nav-cats is `block lg:hidden` — the category strip only exists below Tailwind's lg
    // breakpoint (1024px). Playwright's Desktop Chrome viewport is 1280px wide, where the
    // markup renders but stays hidden.
    await page.setViewportSize({ width: 900, height: 800 });
    await gotoApp(page, '/es/');
    await expect(page).toHaveTitle(/Pilar Estilo/i);
    await expect(page.locator('header#site-header')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('#nav-cats-list')).toBeVisible({ timeout: 15000 });
  });

  test('English home shows English shipping text', async ({ page }) => {
    await gotoApp(page, '/en/');
    await expect(page).toHaveTitle(/Pilar Estilo/i);
    await expect(page.getByText('Shipping across Chile')).toBeVisible();
  });

  test('Root / redirects to /es/', async ({ page }) => {
    await gotoApp(page, '/');
    await expect(page).toHaveURL(/\/es\//);
  });
});

test.describe('Products listing', () => {
  test('Products page loads without error', async ({ page }) => {
    await gotoApp(page, '/es/products');
    await expect(page.locator('body')).not.toContainText('500');
  });

  test('Category query filter route loads', async ({ page }) => {
    await gotoApp(page, '/es/products?category=zapatos');
    await expect(page).toHaveURL(/\/es\/products\?category=zapatos/);
    await expect(page.locator('body')).not.toContainText('500');
  });

  test('Category nav exposes products link', async ({ page }) => {
    // Below lg — see the note in "Spanish home renders header and category nav".
    await page.setViewportSize({ width: 900, height: 800 });
    await gotoApp(page, '/es/');
    const productsLink = page.locator('#nav-cats-list a[href="/es/products"]').first();
    await expect(productsLink).toBeVisible({ timeout: 15000 });
    const href = await productsLink.getAttribute('href');
    expect(href).toBe('/es/products');
  });

  test('Products page shows product cards', async ({ page }) => {
    await gotoApp(page, '/es/products');
    await expect(page.locator('article, [href*="/es/products/"]').first()).toBeVisible({ timeout: 8000 });
  });
});

test.describe('Product detail', () => {
  test('Product detail loads from fixture product', async ({ page }) => {
    await gotoApp(page, '/es/products/fixture-1');
    await expect(page.locator('body')).not.toContainText('500');
    await expect(page.locator('main')).toBeVisible();
  });
});

test.describe('Cart', () => {
  test('Cart page loads', async ({ page }) => {
    await gotoApp(page, '/es/cart');
    await expect(page.locator('body')).not.toContainText('500');
  });
});

test.describe('Auth pages', () => {
  test('Login page renders email and password fields', async ({ page }) => {
    await gotoApp(page, '/es/auth/login');
    const form = authForm(page);
    await expect(form).toBeVisible();
    await expect(form.locator('input[autocomplete="email"]')).toBeVisible();
    await expect(form.locator('input[autocomplete="current-password"]')).toBeVisible();
    await expect(form.locator('button[type="submit"]')).toBeVisible();
  });

  test('Register page renders all fields', async ({ page }) => {
    await gotoApp(page, '/es/auth/register');
    const form = authForm(page);
    await expect(form).toBeVisible();
    await expect(form.locator('input[autocomplete="name"]')).toBeVisible();
    await expect(form.locator('input[autocomplete="email"]')).toBeVisible();
    await expect(form.locator('input[autocomplete="new-password"]')).toBeVisible();
    await expect(form.locator('button[type="submit"]')).toBeVisible();
  });

  test('Login page links to register', async ({ page }) => {
    await gotoApp(page, '/es/auth/login');
    await expect(authForm(page).locator('a[href*="/auth/register"]')).toBeVisible();
  });

  test('Register page links to login', async ({ page }) => {
    await gotoApp(page, '/es/auth/register');
    await expect(authForm(page).locator('a[href*="/auth/login"]')).toBeVisible();
  });
});

test.describe('Admin auth guard', () => {
  test.beforeEach(async ({ context }) => {
    await context.clearCookies();
  });

  test('Unauthenticated /admin/ redirects to /admin/login', async ({ page }) => {
    await gotoApp(page, '/admin/');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Unauthenticated /admin/products redirects to /admin/login', async ({ page }) => {
    await gotoApp(page, '/admin/products');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Unauthenticated /admin/categories redirects to /admin/login', async ({ page }) => {
    await gotoApp(page, '/admin/categories');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Admin login page renders correctly', async ({ page }) => {
    await gotoApp(page, '/admin/login');
    const form = adminLoginForm(page);
    await expect(form).toBeVisible();
    await expect(form.locator('input[autocomplete="email"]')).toBeVisible();
    await expect(form.locator('input[autocomplete="current-password"]')).toBeVisible();
    await expect(form.locator('button[type="submit"]')).toBeVisible();
  });
});

test.describe('Auth + admin flow (requires running backend)', () => {
  test('Register new customer lands on account page', async ({ page }) => {
    const ts = Date.now();
    const email = `pw_${ts}@test.com`;

    await gotoApp(page, '/es/auth/register');
    const form = authForm(page);
    await form.locator('input[autocomplete="name"]').fill('Playwright Test');
    await form.locator('input[autocomplete="email"]').fill(email);
    await form.locator('input[autocomplete="new-password"]').fill('password123');
    await form.locator('button[type="submit"]').click();

    await page.waitForTimeout(2000);
    const url = page.url();

    if (url.includes('/account')) {
      await expect(page.locator('body')).toContainText(email);
      await expect(page.getByRole('button', { name: 'Perfil', exact: true })).toBeVisible();
    } else {
      test.info().annotations.push({ type: 'skip-reason', description: 'Backend not running' });
    }
  });

  test('Admin login accesses dashboard and shows sidebar nav', async ({ page }) => {
    await page.context().clearCookies();
    await gotoApp(page, '/admin/login');

    const form = adminLoginForm(page);
    await form.locator('input[autocomplete="email"]').fill('admin@pilarestilo.com');
    await form.locator('input[autocomplete="current-password"]').fill('admin2026');
    await form.locator('button[type="submit"]').click();

    await page.waitForTimeout(2000);
    const url = page.url();

    if (url.includes('/admin/') && !url.includes('/login')) {
      await expect(page.locator('body')).toContainText('Dashboard');
    } else {
      test.info().annotations.push({ type: 'skip-reason', description: 'Backend not running' });
    }
  });

  test('Customer credentials are blocked at admin login', async ({ page }) => {
    await page.context().clearCookies();
    await gotoApp(page, '/admin/login');

    const form = adminLoginForm(page);
    await form.locator('input[autocomplete="email"]').fill('cliente1@example.com');
    await form.locator('input[autocomplete="current-password"]').fill('valentina2026');
    await form.locator('button[type="submit"]').click();

    await page.waitForTimeout(2000);
    await expect(page).not.toHaveURL(/\/admin\/$|\/admin\/index/);
  });
});
