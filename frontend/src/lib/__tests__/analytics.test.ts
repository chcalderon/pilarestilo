import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  track,
  identifyCustomer,
  resetAnalytics,
  isAnalyticsOptedOut,
  setAnalyticsOptOut,
} from '../analytics';

type FakePostHog = {
  capture: ReturnType<typeof vi.fn>;
  identify: ReturnType<typeof vi.fn>;
  reset: ReturnType<typeof vi.fn>;
  opt_in_capturing: ReturnType<typeof vi.fn>;
  opt_out_capturing: ReturnType<typeof vi.fn>;
};

function installFakePostHog(): FakePostHog {
  const ph: FakePostHog = {
    capture: vi.fn(),
    identify: vi.fn(),
    reset: vi.fn(),
    opt_in_capturing: vi.fn(),
    opt_out_capturing: vi.fn(),
  };
  (window as unknown as { posthog?: unknown }).posthog = ph;
  return ph;
}

afterEach(() => {
  delete (window as unknown as { posthog?: unknown }).posthog;
  try {
    window.localStorage.clear();
  } catch {
    /* no storage in this env */
  }
});

describe('analytics wrapper', () => {
  it('track no-ops when PostHog is absent', () => {
    expect(() => track('cart_viewed', { a: 1 })).not.toThrow();
  });

  it('track no-ops while the snippet stub is up but array.js has not loaded', () => {
    (window as unknown as { posthog?: unknown }).posthog = { capture: undefined };
    expect(() => track('cart_viewed')).not.toThrow();
  });

  it('track forwards to posthog.capture once loaded', () => {
    const ph = installFakePostHog();
    track('add_to_cart', { product_id: 'p1' });
    expect(ph.capture).toHaveBeenCalledWith('add_to_cart', { product_id: 'p1' });
  });

  it('identifyCustomer forwards a non-empty id and ignores an empty one', () => {
    const ph = installFakePostHog();
    identifyCustomer('cust-1');
    identifyCustomer('');
    expect(ph.identify).toHaveBeenCalledTimes(1);
    expect(ph.identify).toHaveBeenCalledWith('cust-1');
  });

  it('resetAnalytics forwards to posthog.reset', () => {
    const ph = installFakePostHog();
    resetAnalytics();
    expect(ph.reset).toHaveBeenCalledTimes(1);
  });

  it('setAnalyticsOptOut persists the flag and toggles capture both ways', () => {
    const ph = installFakePostHog();

    setAnalyticsOptOut(true);
    expect(isAnalyticsOptedOut()).toBe(true);
    expect(ph.opt_out_capturing).toHaveBeenCalledTimes(1);

    setAnalyticsOptOut(false);
    expect(isAnalyticsOptedOut()).toBe(false);
    expect(ph.opt_in_capturing).toHaveBeenCalledTimes(1);
  });

  it('isAnalyticsOptedOut is false by default', () => {
    expect(isAnalyticsOptedOut()).toBe(false);
  });
});
