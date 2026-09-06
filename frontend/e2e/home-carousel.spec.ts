import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

/*
 * The "Por Categoría" carousel is a self-contained inline script + <style> block in
 * src/pages/[locale]/index.astro. Testing it through the full homepage needs a backend to
 * supply categories, so instead this spec lifts the REAL script and CSS out of the .astro file
 * and drops them onto a minimal fixture page. No drift: the code under test is the shipped code.
 */
const astro = readFileSync('src/pages/[locale]/index.astro', 'utf8').replace(/\r\n/g, '\n');
const script = astro.match(/<script is:inline>\s*\n([\s\S]*?)\n\s*<\/script>/)?.[1];
const style = astro.match(/<style>\s*\n([\s\S]*?\.pe-carousel-slide[\s\S]*?)\n\s*<\/style>/)?.[1];

if (!script || !style) {
  throw new Error('Could not extract the carousel script/style from index.astro — the markers moved');
}

function fixture(slideCount = 5): string {
  const slides = Array.from({ length: slideCount }, (_, i) =>
    `<article class="pe-carousel-slide"><a href="/cat-${i}">Categoría ${i}</a></article>`).join('');
  const dots = Array.from({ length: slideCount }, (_, i) =>
    `<button type="button" data-pe-carousel-dot="${i}" class="${i === 0 ? 'w-6' : 'w-3'}"></button>`).join('');
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
    :root { --pe-surface:#fff; --pe-ink:#111; --pe-border:#ddd; }
    ${style}
    .pe-carousel-slide { height: 220px; background:#eee; }
  </style></head><body>
    <div data-pe-carousel class="pe-carousel relative" role="region" aria-label="Carrusel de categorías">
      <div class="overflow-hidden px-0 sm:px-1 pb-3">
        <div data-pe-carousel-track class="pe-carousel-track">${slides}</div>
      </div>
      <div data-pe-carousel-controls class="mt-6 flex items-center justify-between gap-4">
        <div data-pe-carousel-dots>${dots}</div>
        <button type="button" data-pe-carousel-toggle
          data-label-play="Reproducir" data-label-pause="Pausar" data-hint="Pausar o reanudar">Pausar</button>
        <button type="button" data-pe-carousel-prev aria-label="Anterior">‹</button>
        <button type="button" data-pe-carousel-next aria-label="Siguiente">›</button>
      </div>
    </div>
    <script>${script}</script>
  </body></html>`;
}

const transform = (page: Page) =>
  page.evaluate(() => document.querySelector<HTMLElement>('[data-pe-carousel-track]')!.style.transform);

const realSlideVisible = (page: Page) =>
  page.evaluate(() => {
    const track = document.querySelector<HTMLElement>('[data-pe-carousel-track]')!;
    const wrap = track.parentElement!.getBoundingClientRect();
    // some non-clone slide overlaps the viewport window of the wrapper
    return Array.from(track.children).some((el) => {
      if ((el as HTMLElement).classList.contains('is-clone')) return false;
      const r = el.getBoundingClientRect();
      return r.right > wrap.left + 1 && r.left < wrap.right - 1;
    });
  });

test.describe('Homepage category carousel', () => {
  test('autoplays by default and advances the track', async ({ page }) => {
    await page.setContent(fixture(), { waitUntil: 'load' });
    const before = await transform(page);
    await page.waitForTimeout(5000);
    expect(await transform(page)).not.toBe(before);
    await expect(page.locator('[data-pe-carousel-toggle]')).toHaveAttribute('aria-pressed', 'true');
  });

  test('Pausar stops it, Reproducir starts it again', async ({ page }) => {
    await page.setContent(fixture(), { waitUntil: 'load' });
    await page.locator('[data-pe-carousel-toggle]').click(); // -> Reproducir (paused)
    const paused = await transform(page);
    await page.waitForTimeout(5000);
    expect(await transform(page)).toBe(paused);

    await page.locator('[data-pe-carousel-toggle]').click(); // -> Pausar (playing)
    await page.waitForTimeout(5000);
    expect(await transform(page)).not.toBe(paused);
  });

  test.describe('with prefers-reduced-motion: reduce', () => {
    test.beforeEach(async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
    });

    test('the OS setting is actually emulated', async ({ page }) => {
      await page.setContent(fixture(), { waitUntil: 'load' });
      expect(await page.evaluate(() =>
        window.matchMedia('(prefers-reduced-motion: reduce)').matches)).toBe(true);
    });

    test('still autoplays by default (owner decision 2026-09-06)', async ({ page }) => {
      await page.setContent(fixture(), { waitUntil: 'load' });
      const before = await transform(page);
      await page.waitForTimeout(5000);
      expect(await transform(page)).not.toBe(before);
    });

    test('the toggle can pause and resume it', async ({ page }) => {
      await page.setContent(fixture(), { waitUntil: 'load' });
      await page.locator('[data-pe-carousel-toggle]').click();
      const paused = await transform(page);
      await page.waitForTimeout(5000);
      expect(await transform(page)).toBe(paused);

      await page.locator('[data-pe-carousel-toggle]').click();
      await page.waitForTimeout(5000);
      expect(await transform(page)).not.toBe(paused);
    });

    test('never runs off into the clones — a real slide stays on screen', async ({ page }) => {
      await page.setContent(fixture(3), { waitUntil: 'load' });
      // 3 slides, 3 clones each side, 4s cadence: 12s = 3 advances, past the trailing clones.
      await page.waitForTimeout(13000);
      expect(await realSlideVisible(page)).toBe(true);
    });
  });
});
