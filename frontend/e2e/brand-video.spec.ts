import { test, expect } from '@playwright/test';

test.describe('Homepage brand video', () => {
  test('Spanish home renders the brand video, muted and without an autoplay attribute', async ({ page }) => {
    await page.goto('/es/', { waitUntil: 'domcontentloaded' });

    const video = page.locator('figure[data-pe-brandvideo] video');
    await expect(video).toHaveCount(1);

    // The section sits low on the page; scroll it into view so any lazy behaviour kicks in.
    await video.scrollIntoViewIfNeeded();

    await expect(video).toHaveAttribute('poster', '/media/pilar-estilo-marca-poster.jpg');
    await expect(video).toHaveJSProperty('muted', true);
    // Playback is started by script (IntersectionObserver), never by the HTML attribute — a bare
    // autoplay attribute would fire before the section is on screen.
    expect(await video.getAttribute('autoplay')).toBeNull();

    const sources = await video.locator('source').evaluateAll((els) =>
      els.map((el) => (el as HTMLSourceElement).getAttribute('src')),
    );
    expect(sources).toEqual([
      '/media/pilar-estilo-marca.webm',
      '/media/pilar-estilo-marca.mp4',
    ]);
  });

  test('reduced-motion visitors get a play control instead of autoplay', async ({ browser }) => {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    const page = await context.newPage();
    await page.goto('/es/', { waitUntil: 'domcontentloaded' });

    const video = page.locator('figure[data-pe-brandvideo] video');
    await video.scrollIntoViewIfNeeded();

    await expect(page.locator('[data-pe-brandvideo-play]')).toBeVisible();
    await expect(video).toHaveJSProperty('paused', true);

    await context.close();
  });

  test('the sound toggle unmutes the video', async ({ page }) => {
    await page.goto('/es/', { waitUntil: 'domcontentloaded' });

    const video = page.locator('figure[data-pe-brandvideo] video');
    await video.scrollIntoViewIfNeeded();

    const soundBtn = page.locator('[data-pe-brandvideo-sound]');
    await expect(soundBtn).toBeVisible();
    await expect(soundBtn).toHaveAttribute('aria-pressed', 'false');

    await soundBtn.click();

    await expect(soundBtn).toHaveAttribute('aria-pressed', 'true');
    await expect(video).toHaveJSProperty('muted', false);
  });
});
