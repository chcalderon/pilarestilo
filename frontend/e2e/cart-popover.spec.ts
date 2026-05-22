import { test, expect } from '@playwright/test';

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';

test.describe('Cart popover', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/es/`);
  });

  test.describe('desktop', () => {
    test.use({ viewport: { width: 1280, height: 800 } });

    test('opens on hover after intent delay', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.hover();
      // hover-intent delay is 200ms
      await page.waitForTimeout(300);
      await expect(page.getByRole('dialog', { name: /carrito/i })).toBeVisible();
    });

    test('closes on ESC', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.hover();
      await page.waitForTimeout(300);
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog', { name: /carrito/i })).not.toBeVisible();
    });

    test('closes on click outside', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.hover();
      await page.waitForTimeout(300);
      await page.mouse.click(50, 50);
      await expect(page.getByRole('dialog', { name: /carrito/i })).not.toBeVisible();
    });

    test('shows empty state when cart is empty', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.hover();
      await page.waitForTimeout(300);
      await expect(page.getByText(/vacío|empty/i)).toBeVisible();
    });
  });

  test.describe('mobile', () => {
    test.use({ viewport: { width: 375, height: 812 } });

    test('opens bottom-sheet on tap', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.tap();
      await expect(page.getByRole('dialog', { name: /carrito/i })).toBeVisible();
    });

    test('closes on backdrop tap', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.tap();
      await expect(page.getByRole('dialog', { name: /carrito/i })).toBeVisible();
      // Tap backdrop (top area of screen)
      await page.mouse.click(187, 50);
      await expect(page.getByRole('dialog', { name: /carrito/i })).not.toBeVisible();
    });

    test('shows empty state when cart is empty', async ({ page }) => {
      const trigger = page.getByRole('button', { name: /carrito/i });
      await trigger.tap();
      await expect(page.getByText(/vacío|empty/i)).toBeVisible();
    });
  });
});
