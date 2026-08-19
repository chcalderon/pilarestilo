/**
 * Measures real contrast in a real browser, in both themes.
 *
 * Reading hex values out of the source cannot answer this: a colour is only legible against the
 * thing actually painted behind it, which is decided by inherited backgrounds, alpha, and whatever
 * the theme resolved the tokens to. So this walks the rendered page, finds the surface each piece
 * of text really sits on, and applies the WCAG ratio.
 *
 *   TEST_BASE_URL=https://localhost node scripts/contrast-audit.mjs
 *
 * Findings are grouped by cause rather than by sighting: the admin sidebar is drawn on thirteen
 * pages and is one thing to fix, not thirteen.
 */
import { chromium } from '@playwright/test';

const BASE = process.env.TEST_BASE_URL ?? 'https://localhost';
const ADMIN = { email: 'admin@pilarestilo.com', password: 'admin2026' };

const STOREFRONT = [
  '/es/', '/es/products', '/es/cart', '/es/auth/register', '/es/auth/login',
  '/es/privacy', '/es/shipping-returns', '/en/',
];
const ADMIN_PAGES = [
  '/admin/', '/admin/products', '/admin/categories', '/admin/ventas', '/admin/payments',
  '/admin/devoluciones', '/admin/privacidad', '/admin/despachos', '/admin/caja',
  '/admin/users', '/admin/discounts', '/admin/settings', '/admin/reviews',
];

const MEASURE = () => {
  /*
   * Colours are resolved through a canvas rather than parsed. Chrome returns oklab() and color()
   * for anything that went through a colour function, and pulling numbers out of those with a
   * regex yields ratios below 1, which is arithmetically impossible. The canvas converts any CSS
   * colour to sRGB, which is the space the WCAG formula is defined in.
   */
  const probe = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
  const cache = new Map();

  const parse = (value) => {
    const key = String(value ?? '');
    if (cache.has(key)) return cache.get(key);
    let result = null;
    if (key && key !== 'none' && key !== 'transparent') {
      probe.globalCompositeOperation = 'copy';
      probe.fillStyle = key;
      probe.fillRect(0, 0, 1, 1);
      const [r, g, b, alpha] = probe.getImageData(0, 0, 1, 1).data;
      result = { r, g, b, a: alpha / 255 };
    }
    cache.set(key, result);
    return result;
  };

  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  });

  const lum = ({ r, g, b }) => {
    const f = (c) => {
      const s = c / 255;
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };

  const ratio = (a, b) => {
    const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
  };

  /** What is actually painted behind this element, compositing every translucent ancestor. */
  const surfaceUnder = (el) => {
    const stack = [];
    let node = el;
    while (node) {
      const bg = parse(getComputedStyle(node).backgroundColor);
      if (bg && bg.a > 0) {
        stack.push(bg);
        if (bg.a === 1) break;
      }
      node = node.parentElement;
    }
    let base = { r: 255, g: 255, b: 255, a: 1 };
    for (let i = stack.length - 1; i >= 0; i--) base = over(stack[i], base);
    return base;
  };

  const findings = [];
  const seen = new Set();

  for (const el of document.querySelectorAll('body *')) {
    const style = getComputedStyle(el);
    if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) continue;

    const text = Array.from(el.childNodes)
      .filter((n) => n.nodeType === Node.TEXT_NODE)
      .map((n) => n.textContent.trim())
      .join(' ')
      .trim();
    if (!text) continue;

    const box = el.getBoundingClientRect();
    if (box.width < 4 || box.height < 4) continue;

    const fg = parse(style.color);
    if (!fg || fg.a === 0) continue;

    const bg = surfaceUnder(el);
    const value = ratio(over(fg, bg), bg);

    const size = parseFloat(style.fontSize);
    const weight = Number(style.fontWeight) || 400;
    const large = size >= 24 || (size >= 18.66 && weight >= 700);
    const required = large ? 3 : 4.5;
    if (value >= required) continue;

    const cls = typeof el.className === 'string' ? el.className : '';
    const key = `${style.color}|${cls}|${text.slice(0, 40)}`;
    if (seen.has(key)) continue;
    seen.add(key);

    findings.push({
      // Marked so the run can go back and look at the pixels actually painted there.
      needsPixelCheck: true,
      rect: { x: box.x, y: box.y, w: box.width, h: box.height },
      text: text.slice(0, 60),
      tag: el.tagName.toLowerCase(),
      cls: cls.slice(0, 80),
      color: style.color,
      on: `rgb(${Math.round(bg.r)}, ${Math.round(bg.g)}, ${Math.round(bg.b)})`,
      size: Math.round(size),
      ratio: Math.round(value * 100) / 100,
      required,
    });
  }
  return findings;
};

const browser = await chromium.launch();
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 900 } });

const login = await context.request.post(`${BASE}/api/auth/login`, { data: ADMIN });
const token = (await login.json()).accessToken;
await context.addCookies([{ name: 'pe_token', value: token, url: BASE }]);

const byCause = new Map();
let sightings = 0;

for (const theme of ['dark', 'light']) {
  const themed = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1280, height: 900 },
    storageState: await context.storageState(),
  });
  await themed.addInitScript((t) => {
    try {
      localStorage.setItem('pe-theme', t);
    } catch {
      /* storage off: the page falls back to its default, which the check below catches */
    }
  }, theme);
  const page = await themed.newPage();

  for (const path of [...STOREFRONT, ...ADMIN_PAGES]) {
    try {
      await page.goto(BASE + path, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.waitForTimeout(1200);
      const applied = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
      if (applied !== theme) console.log(`  !! ${path}: el tema quedó en ${applied}, no ${theme}`);

      const found = await page.evaluate(MEASURE);

      /*
       * A second look, at the pixels. The CSS walk climbs ancestors, so it cannot see a sibling
       * scrim or a photograph painted behind the text — every label over an image came back as
       * unreadable when the gradient above it was doing its job. Here the element's own box is
       * screenshotted and its most common colour taken as the real background, which is what the
       * eye is up against.
       */
      for (const f of found) {
        if (!f.needsPixelCheck || f.rect.w < 2 || f.rect.h < 2) continue;
        try {
          const shot = await page.screenshot({
            clip: {
              x: Math.max(0, f.rect.x),
              y: Math.max(0, f.rect.y),
              width: Math.min(f.rect.w, 1200),
              height: Math.min(f.rect.h, 200),
            },
          });
          const painted = await page.evaluate(async (bytes) => {
            const blob = new Blob([new Uint8Array(bytes)], { type: 'image/png' });
            const bitmap = await createImageBitmap(blob);
            const c = document.createElement('canvas');
            c.width = bitmap.width;
            c.height = bitmap.height;
            const ctx = c.getContext('2d', { willReadFrequently: true });
            ctx.drawImage(bitmap, 0, 0);
            const data = ctx.getImageData(0, 0, c.width, c.height).data;
            const tally = new Map();
            for (let i = 0; i < data.length; i += 4) {
              // Quantised, so a photograph's noise still lands on one dominant tone.
              const key = `${data[i] >> 4}|${data[i + 1] >> 4}|${data[i + 2] >> 4}`;
              tally.set(key, (tally.get(key) ?? 0) + 1);
            }
            const [best] = [...tally.entries()].sort((a, b) => b[1] - a[1])[0];
            const [r, g, b] = best.split('|').map((v) => (Number(v) << 4) + 8);
            return { r, g, b };
          }, Array.from(shot));

          const lum = ({ r, g, b }) => {
            const ch = (v) => {
              const x = v / 255;
              return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
            };
            return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b);
          };
          const fg = f.color.match(/[\d.]+/g);
          if (!fg) continue;
          const ink = { r: Number(fg[0]), g: Number(fg[1]), b: Number(fg[2]) };
          const [hi, lo] = [lum(ink), lum(painted)].sort((a, b) => b - a);
          f.pixelRatio = Math.round(((hi + 0.05) / (lo + 0.05)) * 100) / 100;
          f.on = `pixeles rgb(${painted.r}, ${painted.g}, ${painted.b})`;
        } catch {
          // A clip outside the viewport is not worth failing the audit over.
        }
      }

      for (const f of found) {
        // The pixel reading wins when it exists: it is what somebody actually sees.
        if (f.pixelRatio !== undefined) {
          if (f.pixelRatio >= f.required) continue;
          f.ratio = f.pixelRatio;
        }
        sightings++;
        const cause = `${theme}|${f.color}|${f.on}|${f.cls || f.tag}`;
        const known = byCause.get(cause);
        if (known) {
          known.pages.add(path);
          if (f.ratio < known.ratio) known.ratio = f.ratio;
        } else {
          byCause.set(cause, { ...f, theme, pages: new Set([path]) });
        }
      }
    } catch (err) {
      console.log(`  !! ${path}: ${err.message.split('\n')[0]}`);
    }
  }
  await themed.close();
}

const causes = [...byCause.values()].sort((a, b) => a.ratio - b.ratio);
console.log(`\n${'='.repeat(76)}`);
console.log(`  ${causes.length} causas distintas, ${sightings} apariciones`);
console.log('='.repeat(76));
for (const c of causes) {
  const where = [...c.pages];
  console.log(`\n  [${c.theme}] ${c.ratio} / ${c.required}   ${c.color} sobre ${c.on}   ${c.size}px  <${c.tag}> "${c.text}"`);
  if (c.cls) console.log(`      ${c.cls}`);
  console.log(`      ${where.length} pág: ${where.slice(0, 4).join(', ')}${where.length > 4 ? ', …' : ''}`);
}

await browser.close();
