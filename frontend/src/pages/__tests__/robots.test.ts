import { describe, expect, it } from 'vitest';
import { GET } from '../robots.txt';

async function body(): Promise<string> {
  const res = await GET({
    request: new Request('https://pilarestilo.com/robots.txt'),
  } as Parameters<typeof GET>[0]);
  return res.text();
}

describe('robots.txt', () => {
  it('lets Googlebot reach product images but not the rest of the API', async () => {
    const txt = await body();
    const googlebot = txt.split('\n\n').find((g) => g.startsWith('User-agent: Googlebot\n'));
    expect(googlebot).toBeDefined();
    expect(googlebot).toContain('Disallow: /api/');
    expect(googlebot).toContain('Allow: /api/media/');
  });

  it('keeps the panel and the account/checkout flows out of every crawler', async () => {
    const txt = await body();
    for (const path of ['/admin/', '/*/checkout', '/*/cart', '/*/auth/']) {
      expect(txt).toContain(`Disallow: ${path}`);
    }
  });

  it('points at the sitemap', async () => {
    expect(await body()).toContain('Sitemap: https://pilarestilo.com/sitemap.xml');
  });
});
