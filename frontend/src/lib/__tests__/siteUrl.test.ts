import { describe, expect, it } from 'vitest';
import { resolveSiteUrl, absoluteUrl, canonicalUrlFor } from '../siteUrl';

// PUBLIC_SITE_URL is unset in the test env, so every case falls back to the request origin.

describe('resolveSiteUrl', () => {
  it('uses the request origin when nothing is configured', () => {
    expect(resolveSiteUrl(new URL('https://pilarestilo.com/es/products/abc?x=1'))).toBe(
      'https://pilarestilo.com',
    );
  });

  it('accepts a string URL', () => {
    expect(resolveSiteUrl('http://localhost:4321/es/')).toBe('http://localhost:4321');
  });

  it('falls back to localhost with no request', () => {
    expect(resolveSiteUrl()).toBe('http://localhost:4321');
  });

  it('trusts X-Forwarded-Host / X-Forwarded-Proto over the internal request origin', () => {
    const headers = new Headers({
      host: 'frontend:4321',
      'x-forwarded-host': 'pilarestilo.com',
      'x-forwarded-proto': 'https',
    });
    expect(resolveSiteUrl(new URL('http://frontend:4321/sitemap.xml'), headers)).toBe(
      'https://pilarestilo.com',
    );
  });

  it('uses the plain Host header when there is no forwarded host', () => {
    const headers = new Headers({ host: 'pilarestilo.com' });
    expect(resolveSiteUrl(new URL('https://frontend:4321/'), headers)).toBe(
      'https://pilarestilo.com',
    );
  });

  it('takes the first entry when a forwarded header is a list', () => {
    const headers = new Headers({
      'x-forwarded-host': 'pilarestilo.com, proxy.internal',
      'x-forwarded-proto': 'https, http',
    });
    expect(resolveSiteUrl(new URL('http://frontend/'), headers)).toBe('https://pilarestilo.com');
  });
});

describe('absoluteUrl', () => {
  const req = new URL('https://pilarestilo.com/es/products/abc');

  it('prefixes a site-relative path', () => {
    expect(absoluteUrl('/api/media/products/x.jpg', req)).toBe(
      'https://pilarestilo.com/api/media/products/x.jpg',
    );
  });

  it('adds a missing leading slash', () => {
    expect(absoluteUrl('logo.png', req)).toBe('https://pilarestilo.com/logo.png');
  });

  it('leaves an already-absolute URL untouched', () => {
    expect(absoluteUrl('https://cdn.example.com/x.jpg', req)).toBe('https://cdn.example.com/x.jpg');
  });
});

describe('canonicalUrlFor', () => {
  it('drops the query string', () => {
    expect(canonicalUrlFor(new URL('https://pilarestilo.com/es/products?page=2'))).toBe(
      'https://pilarestilo.com/es/products',
    );
  });

  it('drops a trailing slash but keeps the root', () => {
    expect(canonicalUrlFor(new URL('https://pilarestilo.com/es/products/'))).toBe(
      'https://pilarestilo.com/es/products',
    );
    expect(canonicalUrlFor(new URL('https://pilarestilo.com/'))).toBe('https://pilarestilo.com/');
  });
});
