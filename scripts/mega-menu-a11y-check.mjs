import { chromium } from '../frontend/node_modules/playwright/index.mjs';

process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const BASE_URL = 'https://localhost';

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });

  await page.goto(`${BASE_URL}/es`, { waitUntil: 'networkidle' });
  const trigger = page.locator('.mega-trigger[data-slug="mujer"]').first();
  await trigger.focus();
  await page.keyboard.press('ArrowDown');
  await page.locator('.mega-tray').waitFor({ state: 'visible', timeout: 10000 });
  await page.waitForTimeout(300);

  const focusState = await page.evaluate(() => {
    const active = document.activeElement;
    return {
      text: active?.textContent?.trim() ?? '',
      inTray: Boolean(active instanceof HTMLElement && active.closest('.mega-tray')),
    };
  });

  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);

  const closeState = await page.evaluate(() => {
    const triggerElement = document.querySelector('.mega-trigger[data-slug="mujer"]');
    const active = document.activeElement;
    return {
      expanded: triggerElement?.getAttribute('aria-expanded'),
      focusOnTrigger: active === triggerElement,
      trayVisible: Boolean(document.querySelector('.mega-tray')),
    };
  });

  console.log(JSON.stringify({ focusState, closeState }, null, 2));
  await browser.close();
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
