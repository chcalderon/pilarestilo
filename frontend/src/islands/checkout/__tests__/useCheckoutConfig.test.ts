import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCheckoutConfig } from '../useCheckoutConfig';

/**
 * The order's paymentMethod used to always be the generic 'WEBPAY' regardless of which gateway
 * was actually configured -- an order paid through Mercado Pago showed "WebPay" in order
 * history. gatewayMethod is the fix: it names the real active provider, the same way
 * gatewayLabel already named it for display.
 */

const getPublicStoreSettings = vi.fn();
const getPublicShippingConfig = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    getPublicStoreSettings: (...args: unknown[]) => getPublicStoreSettings(...args),
    getPublicShippingConfig: (...args: unknown[]) => getPublicShippingConfig(...args),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
  getPublicShippingConfig.mockResolvedValue({
    zones: [{ code: 'LOCAL', name: 'Local', active: true }],
    couriers: [{ id: 'c1', name: 'Starken', active: true }],
    paymentMode: 'POR_PAGAR',
  });
});

describe('useCheckoutConfig: gatewayMethod', () => {
  it('resolves to MERCADOPAGO when that is the active provider', async () => {
    getPublicStoreSettings.mockResolvedValue({ paymentGatewayProviders: ['MERCADO_PAGO'] });

    const { result } = renderHook(() => useCheckoutConfig());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.gatewayMethod).toBe('MERCADOPAGO');
    expect(result.current.gatewayLabel).toBe('Mercado Pago');
  });

  it('falls back to WEBPAY for a provider it does not recognise yet', async () => {
    getPublicStoreSettings.mockResolvedValue({ paymentGatewayProviders: ['TUU'] });

    const { result } = renderHook(() => useCheckoutConfig());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.gatewayMethod).toBe('WEBPAY');
  });

  it('defaults to MERCADOPAGO when no provider list comes back at all', async () => {
    getPublicStoreSettings.mockResolvedValue({});

    const { result } = renderHook(() => useCheckoutConfig());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.gatewayMethod).toBe('MERCADOPAGO');
  });
});
