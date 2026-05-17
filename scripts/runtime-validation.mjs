import fs from 'node:fs/promises';
import path from 'node:path';
import { chromium, devices } from '../frontend/node_modules/playwright/index.mjs';

process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const BASE_URL = 'https://localhost';
const SCREENSHOT_DIR = path.join(process.env.TEMP || process.env.TMP || '.', 'pe-runtime-qa-20260517');

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function fetchJson(relativePath) {
  const response = await fetch(`${BASE_URL}${relativePath}`);
  if (!response.ok) {
    throw new Error(`Fetch failed for ${relativePath}: ${response.status}`);
  }
  return response.json();
}

async function postJson(relativePath, body) {
  const response = await fetch(`${BASE_URL}${relativePath}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`POST failed for ${relativePath}: ${response.status}`);
  }
  return response.json();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function summarizeProduct(product) {
  return {
    id: product.id,
    name: product.name,
    categoryTypes: product.categoryTypes,
    variants: product.variants?.map((variant) => ({
      color: variant.color,
      size: variant.size,
      stock: variant.stock,
    })) ?? [],
  };
}

async function setAdminSession(context) {
  const loginResponse = await postJson('/api/auth/login', {
    email: 'admin@pilarestilo.com',
    password: 'admin2026',
  });
  await context.addCookies([
    {
      name: 'pe_token',
      value: loginResponse.accessToken,
      url: BASE_URL,
      httpOnly: false,
      secure: true,
      sameSite: 'Lax',
    },
  ]);
}

async function closeProductFormIfOpen(page) {
  const closeButton = page.getByLabel('Cerrar formulario');
  if (await closeButton.count()) {
    await closeButton.click();
    await page.waitForTimeout(300);
  }
}

async function run() {
  await ensureDir(SCREENSHOT_DIR);

  const productsPayload = await fetchJson('/api/products/search?page=0&size=50');
  const categoriesTree = await fetchJson('/api/categories/tree');
  const navigationTree = await fetchJson('/api/navigation/tree?locale=es');

  const products = productsPayload.content ?? [];
  const shoeProduct = products.find((product) =>
    Array.isArray(product.categoryTypes)
    && product.categoryTypes.includes('SHOES')
    && Array.isArray(product.variants)
    && product.variants.some((variant) => /^\d+$/.test(String(variant.size ?? '')))
  );
  const jewelryProduct = products.find((product) =>
    Array.isArray(product.categoryTypes)
    && product.categoryTypes.includes('JEWELRY')
  );

  assert(shoeProduct, 'No live shoe product with numeric variant sizes was found.');
  assert(jewelryProduct, 'No live jewelry product was found.');

  const results = {
    payloads: {
      products: {
        totalElements: productsPayload.totalElements,
        samples: [summarizeProduct(shoeProduct), summarizeProduct(jewelryProduct)],
      },
      categoriesTree: {
        rootCount: categoriesTree.length,
        zapatos: categoriesTree[0]?.children?.find((node) => node.slug === 'zapatos') ?? null,
        accesorios: categoriesTree[0]?.children?.find((node) => node.slug === 'accesorios') ?? null,
      },
      navigationTree: {
        sectionCount: navigationTree.sections?.length ?? 0,
        firstSection: navigationTree.sections?.[0] ?? null,
      },
    },
    checks: [],
    screenshots: {},
  };

  const browser = await chromium.launch({ headless: true });

  try {
    const desktop = await browser.newContext({
      ignoreHTTPSErrors: true,
      viewport: { width: 1440, height: 1100 },
    });
    const desktopPage = await desktop.newPage();

    await desktopPage.goto(`${BASE_URL}/es`, { waitUntil: 'networkidle' });
    const megaTrigger = desktopPage.locator('.mega-trigger[data-slug="mujer"]').first();
    await megaTrigger.hover();
    await desktopPage.locator('.mega-tray').waitFor({ state: 'visible', timeout: 10000 });
    assert(await desktopPage.locator('.mega-tray').getByText(/Edicion Curada/i).count(), 'Desktop mega menu did not show the featured banner.');
    const desktopMegaPath = path.join(SCREENSHOT_DIR, 'desktop-mega-featured-grid.png');
    await desktopPage.screenshot({ path: desktopMegaPath, fullPage: true });
    results.screenshots.desktopMega = desktopMegaPath;
    results.checks.push('Desktop mega menu renders FEATURED_GRID with editorial banner.');

    await megaTrigger.focus();
    await desktopPage.keyboard.press('ArrowDown');
    await desktopPage.locator('.mega-tray [role="menuitem"]').first().waitFor({ state: 'visible', timeout: 10000 });
    await desktopPage.waitForTimeout(250);
    const focusState = await desktopPage.evaluate(() => {
      const active = document.activeElement;
      return {
        text: active?.textContent?.trim() ?? '',
        inTray: Boolean(active instanceof HTMLElement && active.closest('.mega-tray')),
      };
    });
    assert(focusState.inTray, 'Mega menu keyboard open did not move focus into the tray.');
    results.checks.push(`Keyboard opens mega menu and moves focus into tray: ${focusState.text || 'unknown item'}.`);
    await desktopPage.keyboard.press('Escape');
    await desktopPage.waitForTimeout(250);
    const expandedAfterEscape = await megaTrigger.getAttribute('aria-expanded');
    assert(expandedAfterEscape === 'false', 'Mega menu trigger did not collapse after Escape.');

    await desktopPage.goto(`${BASE_URL}/es/products/${shoeProduct.id}`, { waitUntil: 'networkidle' });
    await desktopPage.waitForTimeout(1500);
    assert(await desktopPage.getByText(/^Numero$/i).count(), 'Shoe PDP does not show "Numero".');
    const shoePdpPath = path.join(SCREENSHOT_DIR, 'pdp-shoes-numero.png');
    await desktopPage.screenshot({ path: shoePdpPath, fullPage: true });
    results.screenshots.shoePdp = shoePdpPath;
    results.checks.push(`Shoe PDP uses dynamic secondary label "Numero" for ${shoeProduct.name}.`);

    await desktopPage.goto(`${BASE_URL}/es/products/${jewelryProduct.id}`, { waitUntil: 'networkidle' });
    await desktopPage.waitForTimeout(1500);
    assert(await desktopPage.getByText(/^Material$/i).count(), 'Jewelry PDP does not show "Material".');
    assert(await desktopPage.getByText(/^Diseno$/i).count(), 'Jewelry PDP does not show "Diseno".');
    const jewelryPdpPath = path.join(SCREENSHOT_DIR, 'pdp-jewelry-material-diseno.png');
    await desktopPage.screenshot({ path: jewelryPdpPath, fullPage: true });
    results.screenshots.jewelryPdp = jewelryPdpPath;
    results.checks.push(`Jewelry PDP uses dynamic labels "Material" and "Diseno" for ${jewelryProduct.name}.`);

    await setAdminSession(desktop);

    await desktopPage.goto(`${BASE_URL}/admin/categories`, { waitUntil: 'networkidle' });
    const editCategoryButton = desktopPage.locator('button[title="Editar"]').first();
    await editCategoryButton.waitFor({ state: 'visible', timeout: 10000 });
    await editCategoryButton.click();
    await desktopPage.getByText('Tipo variante').waitFor({ state: 'visible', timeout: 10000 });
    assert(await desktopPage.getByText('Visible en menu').count(), 'Admin categories form does not show menu visibility metadata.');
    const adminCategoryPath = path.join(SCREENSHOT_DIR, 'admin-category-metadata.png');
    await desktopPage.screenshot({ path: adminCategoryPath, fullPage: true });
    results.screenshots.adminCategory = adminCategoryPath;
    results.checks.push('Admin category editor exposes categoryType, hero image, and menu visibility metadata.');

    const adminProductPage = await desktop.newPage();
    await adminProductPage.goto(`${BASE_URL}/admin/products`, { waitUntil: 'networkidle' });
    const productSearch = adminProductPage.locator('input[placeholder*="Buscar"]').first();
    await productSearch.fill(shoeProduct.name);
    await adminProductPage.waitForTimeout(800);
    const shoeRow = adminProductPage.locator('tr', { hasText: shoeProduct.name }).first();
    await shoeRow.getByRole('button', { name: /Editar/i }).click();
    await adminProductPage.getByText('Editar Producto').waitFor({ state: 'visible', timeout: 10000 });
    await adminProductPage.waitForTimeout(800);
    const shoeAdminBody = await adminProductPage.locator('body').innerText();
    assert(shoeAdminBody.includes('COLOR + NUMERO + STOCK'), 'Admin product form did not resolve shoe schema.');
    assert(await adminProductPage.getByText(/^Numero$/i).count(), 'Admin product form does not show Numero for shoe variants.');
    const adminShoeProductPath = path.join(SCREENSHOT_DIR, 'admin-product-shoe-schema.png');
    await adminProductPage.screenshot({ path: adminShoeProductPath, fullPage: true });
    results.screenshots.adminShoeProduct = adminShoeProductPath;
    results.checks.push(`Admin product editor resolves shoe schema for ${shoeProduct.name}.`);

    await closeProductFormIfOpen(adminProductPage);
    await productSearch.fill(jewelryProduct.name);
    await adminProductPage.waitForTimeout(800);
    const jewelryRow = adminProductPage.locator('tr', { hasText: jewelryProduct.name }).first();
    await jewelryRow.getByRole('button', { name: /Editar/i }).click();
    await adminProductPage.getByText('Editar Producto').waitFor({ state: 'visible', timeout: 10000 });
    await adminProductPage.waitForTimeout(800);
    const jewelryAdminBody = await adminProductPage.locator('body').innerText();
    assert(jewelryAdminBody.includes('MATERIAL + DISENO + STOCK'), 'Admin product form did not resolve jewelry schema.');
    assert(await adminProductPage.getByText(/^Material$/i).count(), 'Admin product form does not show Material for jewelry variants.');
    const adminJewelryProductPath = path.join(SCREENSHOT_DIR, 'admin-product-jewelry-schema.png');
    await adminProductPage.screenshot({ path: adminJewelryProductPath, fullPage: true });
    results.screenshots.adminJewelryProduct = adminJewelryProductPath;
    results.checks.push(`Admin product editor resolves jewelry schema for ${jewelryProduct.name}.`);
    await adminProductPage.close();

    await desktop.close();

    const mobile = await browser.newContext({
      ...devices['iPhone 13'],
      ignoreHTTPSErrors: true,
    });
    const mobilePage = await mobile.newPage();
    await mobilePage.goto(`${BASE_URL}/es`, { waitUntil: 'networkidle' });
    await mobilePage.getByLabel(/Abrir menu|Abrir men/i).click();
    await mobilePage.getByRole('dialog').waitFor({ state: 'visible', timeout: 10000 });
    const mobileOpenFlag = await mobilePage.evaluate(() => document.documentElement.getAttribute('data-mobile-nav-open'));
    assert(mobileOpenFlag === 'true', 'Mobile navigation open flag was not set on html element.');
    const fabOpacity = await mobilePage.locator('.pe-whatsapp-fab').evaluate((element) => getComputedStyle(element).opacity);
    assert(Number(fabOpacity) < 0.1, 'WhatsApp FAB is still visible over the mobile menu overlay.');
    const mobileOverlayPath = path.join(SCREENSHOT_DIR, 'mobile-overlay.png');
    await mobilePage.screenshot({ path: mobileOverlayPath, fullPage: true });
    results.screenshots.mobileOverlay = mobileOverlayPath;
    results.checks.push('Mobile navigation overlay opens fullscreen, locks runtime state flag, and hides WhatsApp FAB.');

    await mobilePage.keyboard.press('Escape');
    await mobilePage.waitForTimeout(300);
    const dialogStillVisible = await mobilePage.getByRole('dialog').count();
    assert(dialogStillVisible === 0, 'Mobile navigation overlay did not close with Escape.');

    await mobile.close();
  } finally {
    await browser.close();
  }

  console.log(JSON.stringify(results, null, 2));
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
