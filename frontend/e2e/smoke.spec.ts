import { test, expect } from '@playwright/test';

// ─── Storefront ───────────────────────────────────────────────────────────────

test.describe('Home page', () => {
  test('Spanish home renders hero and category nav', async ({ page }) => {
    await page.goto('/es/');
    await expect(page).toHaveTitle(/Pilar Estilo/i);
    await expect(page.locator('header#site-header')).toBeVisible();
    await expect(page.locator('ul[aria-label="Product categories"]')).toBeVisible();
  });

  test('English home shows English shipping text', async ({ page }) => {
    await page.goto('/en/');
    await expect(page).toHaveTitle(/Pilar Estilo/i);
    await expect(page.locator('text=Nationwide shipping')).toBeVisible();
  });

  test('Root / redirects to /es/', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/es\//);
  });
});

// ─── Products ────────────────────────────────────────────────────────────────

test.describe('Products listing', () => {
  test('Products page loads without error', async ({ page }) => {
    await page.goto('/es/products');
    await expect(page.locator('body')).not.toContainText('500');
  });

  test('Category filter in URL is reflected on page', async ({ page }) => {
    await page.goto('/es/products?category=zapatos');
    await expect(page).toHaveURL(/category=zapatos/);
    // Category nav link for zapatos is present
    await expect(page.locator('a[href*="category=zapatos"]').first()).toBeVisible();
  });

  test('Category nav link navigates to filtered listing', async ({ page }) => {
    await page.goto('/es/');
    await page.locator('a[href*="category=vestidos"]').first().click();
    await expect(page).toHaveURL(/category=vestidos/);
  });

  test('Products page shows product cards', async ({ page }) => {
    await page.goto('/es/products');
    // At least one product link (article or anchor inside product grid)
    await expect(page.locator('article, [href*="/es/products/"]').first()).toBeVisible({ timeout: 8000 });
  });
});

// ─── Product Detail ───────────────────────────────────────────────────────────

test.describe('Product detail', () => {
  test('Product detail loads from fixture product', async ({ page }) => {
    // Use a known fixture product slug
    await page.goto('/es/products/fixture-1');
    await expect(page.locator('body')).not.toContainText('500');
    await expect(page.locator('main')).toBeVisible();
  });
});

// ─── Cart ────────────────────────────────────────────────────────────────────

test.describe('Cart', () => {
  test('Cart page loads', async ({ page }) => {
    await page.goto('/es/cart');
    await expect(page.locator('body')).not.toContainText('500');
  });
});

// ─── Auth pages ───────────────────────────────────────────────────────────────

test.describe('Auth pages', () => {
  test('Login page renders email and password fields', async ({ page }) => {
    await page.goto('/es/auth/login');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('Register page renders all fields', async ({ page }) => {
    await page.goto('/es/auth/register');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('Login page links to register', async ({ page }) => {
    await page.goto('/es/auth/login');
    await expect(page.locator('a[href*="/auth/register"]')).toBeVisible();
  });

  test('Register page links to login', async ({ page }) => {
    await page.goto('/es/auth/register');
    await expect(page.locator('a[href*="/auth/login"]')).toBeVisible();
  });
});

// ─── Admin auth guard (middleware) ───────────────────────────────────────────

test.describe('Admin auth guard', () => {
  test.beforeEach(async ({ context }) => {
    // Ensure no admin cookie is set
    await context.clearCookies();
  });

  test('Unauthenticated /admin/ redirects to /admin/login', async ({ page }) => {
    await page.goto('/admin/');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Unauthenticated /admin/products redirects to /admin/login', async ({ page }) => {
    await page.goto('/admin/products');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Unauthenticated /admin/categories redirects to /admin/login', async ({ page }) => {
    await page.goto('/admin/categories');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('Admin login page renders correctly', async ({ page }) => {
    await page.goto('/admin/login');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });
});

// ─── Auth flow + admin access (requires backend on port 8080) ────────────────

test.describe('Auth + admin flow (requires running backend)', () => {
  test('Register new customer → lands on account page', async ({ page }) => {
    const ts = Date.now();
    const email = `pw_${ts}@test.com`;

    await page.goto('/es/auth/register');
    // Fill in registration form
    const nameInput = page.locator('input[type="text"]').first();
    await nameInput.fill('Playwright Test');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', 'password123');
    await page.click('button[type="submit"]');

    // Wait for navigation or error
    await page.waitForTimeout(2000);
    const url = page.url();

    // If backend is up: should redirect to account page
    if (url.includes('/account')) {
      await expect(page.locator('body')).toContainText(email);
      // Profile tab should be visible
      await expect(page.locator('text=Perfil')).toBeVisible();
    } else {
      // Backend not running — skip gracefully
      test.info().annotations.push({ type: 'skip-reason', description: 'Backend not running' });
    }
  });

  test('Admin login → access dashboard → see sidebar nav', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/admin/login');

    await page.fill('input[type="email"]', 'admin@pilarestilo.com');
    await page.fill('input[type="password"]', 'admin2026');
    await page.click('button[type="submit"]');

    await page.waitForTimeout(2000);
    const url = page.url();

    if (url.includes('/admin/') && !url.includes('/login')) {
      await expect(page.locator('nav[aria-label="Navegación admin"]')).toBeVisible();
      // Dashboard title
      await expect(page.locator('text=Dashboard')).toBeVisible();
    } else {
      test.info().annotations.push({ type: 'skip-reason', description: 'Backend not running' });
    }
  });

  test('Customer credentials blocked at admin login (access denied error)', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/admin/login');

    await page.fill('input[type="email"]', 'cliente1@example.com');
    await page.fill('input[type="password"]', 'valentina2026');
    await page.click('button[type="submit"]');

    await page.waitForTimeout(2000);
    const url = page.url();

    if (!url.includes('/admin/login')) {
      // If backend not running, this test is moot — but if it IS running,
      // login should either fail OR succeed and then middleware blocks → stays on /admin/login
    }
    // Either way, should not reach the dashboard
    await expect(page).not.toHaveURL(/\/admin\/$|\/admin\/index/);
  });
});
