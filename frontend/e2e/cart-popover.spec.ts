import { test, expect, type Page } from '@playwright/test';

const BASE = process.env.TEST_BASE_URL ?? 'http://localhost';

/**
 * The cart trigger is the only button wired to the popover; matching on the /carrito/i
 * accessible name also picked up "Cerrar carrito", "Ver carrito" and the item-count badge,
 * which tripped Playwright's strict mode with 9 matches.
 */
function cartTrigger(page: Page) {
  return page.locator('button[aria-controls="cart-popover-content"]').first();
}

test.describe('Cart popover', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/es/`);
  });

  test.describe('desktop', () => {
    test.use({ viewport: { width: 1280, height: 800 } });

    test('opens on hover after intent delay', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.hover();
      // hover-intent delay is 200ms
      await page.waitForTimeout(300);
      await expect(page.locator('#cart-popover-content')).toBeVisible();
    });

    test('closes on ESC', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.hover();
      await page.waitForTimeout(300);
      await page.keyboard.press('Escape');
      await expect(page.locator('#cart-popover-content')).not.toBeVisible();
    });

    test('closes on click outside', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.hover();
      await page.waitForTimeout(300);
      await page.mouse.click(50, 50);
      await expect(page.locator('#cart-popover-content')).not.toBeVisible();
    });

    test('shows empty state when cart is empty', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.hover();
      await page.waitForTimeout(300);
      await expect(page.getByText(/vacío|empty/i)).toBeVisible();
    });
  });

  test.describe('mobile', () => {
    test.use({ viewport: { width: 375, height: 812 }, hasTouch: true });

    test('opens bottom-sheet on tap', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.tap();
      await expect(page.locator('#cart-popover-content')).toBeVisible();
    });

    test('closes on backdrop tap', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.tap();
      await expect(page.locator('#cart-popover-content')).toBeVisible();
      // Tap backdrop (top area of screen)
      await page.mouse.click(187, 50);
      await expect(page.locator('#cart-popover-content')).not.toBeVisible();
    });

    test('shows empty state when cart is empty', async ({ page }) => {
      const trigger = cartTrigger(page);
      await trigger.tap();
      await expect(page.getByText(/vacío|empty/i)).toBeVisible();
    });
  });
});
