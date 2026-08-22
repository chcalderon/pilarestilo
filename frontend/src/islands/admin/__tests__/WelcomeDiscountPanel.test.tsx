import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import WelcomeDiscountPanel from '../WelcomeDiscountPanel';
import type { SystemSettingsDto } from '../../../lib/api';

/**
 * updateSystemSettings replaces the whole settings row -- a PATCH that carried only the five
 * fields this panel edits would silently wipe every other section (payments, SMTP, tax identity).
 * The regression this guards against: someone trims the payload down to "just what changed" and
 * every other admin tab breaks on next save.
 */

function settings(overrides: Partial<SystemSettingsDto> = {}): SystemSettingsDto {
  return {
    whatsappNumber: '+56900000000',
    paymentMethodBankTransferEnabled: true,
    paymentMethodGatewayEnabled: true,
    paymentGatewayProviders: ['MERCADO_PAGO'],
    paymentGatewayMpAccessTokenConfigured: false,
    paymentGatewayMpWebhookTokenConfigured: false,
    mediaStorageProvider: 'LOCAL',
    mediaS3SecretKeyConfigured: false,
    mediaS3PathStyleEnabled: false,
    notificationProviders: ['LOG'],
    n8nApiKeyConfigured: false,
    whatsappTwilioAuthTokenConfigured: false,
    sendgridApiKeyConfigured: false,
    smtpAuthEnabled: true,
    smtpStarttlsEnabled: true,
    smtpPasswordConfigured: false,
    bankTransferAutoCancelEnabled: true,
    bankTransferAutoCancelTimeoutMinutes: 30,
    bankTransferAutoCancelCron: '0 */15 * * * *',
    taxPayerRut: null,
    taxBusinessName: null,
    taxBusinessActivity: null,
    taxActecoCode: null,
    taxAddress: null,
    taxCommune: null,
    taxCity: null,
    taxVatRate: 19,
    taxDocumentRequiredBeforeDispatch: true,
    taxDocumentProvider: 'MANUAL',
    welcomeDiscountEnabled: false,
    welcomeDiscountType: 'PERCENTAGE',
    welcomeDiscountValue: 10,
    welcomeDiscountMinOrderAmount: 0,
    welcomeDiscountRequiresMarketing: true,
    ...overrides,
  } as SystemSettingsDto;
}

const getSystemSettings = vi.fn();
const updateSystemSettings = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getSystemSettings: (...args: unknown[]) => getSystemSettings(...args),
    updateSystemSettings: (...args: unknown[]) => updateSystemSettings(...args),
  };
});

beforeEach(() => {
  document.cookie = 'pe_token=test-token';
  getSystemSettings.mockReset();
  updateSystemSettings.mockReset();
});

describe('WelcomeDiscountPanel', () => {
  it('leaves the fields disabled when the coupon is off', async () => {
    getSystemSettings.mockResolvedValue(settings({ welcomeDiscountEnabled: false }));

    render(<WelcomeDiscountPanel />);

    const valueInput = await screen.findByLabelText(/valor/i);
    expect(valueInput).toBeDisabled();
  });

  it('enables the fields once the toggle is switched on', async () => {
    getSystemSettings.mockResolvedValue(settings({ welcomeDiscountEnabled: false }));
    const user = userEvent.setup();

    render(<WelcomeDiscountPanel />);
    await screen.findByLabelText(/valor/i);

    await user.click(screen.getByRole('checkbox', { name: /activar cup[oó]n de bienvenida/i }));

    expect(screen.getByLabelText(/valor/i)).toBeEnabled();
  });

  it('saves the whole settings object, not just the five fields it edits', async () => {
    getSystemSettings.mockResolvedValue(settings({
      welcomeDiscountEnabled: false,
      whatsappNumber: '+56911112222',
      taxPayerRut: '76.123.456-7',
    }));
    updateSystemSettings.mockResolvedValue(settings());
    const user = userEvent.setup();

    render(<WelcomeDiscountPanel />);
    await screen.findByLabelText(/valor/i);

    await user.click(screen.getByRole('checkbox', { name: /activar cup[oó]n de bienvenida/i }));
    await user.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => expect(updateSystemSettings).toHaveBeenCalledTimes(1));
    const [payload] = updateSystemSettings.mock.calls[0];
    expect(payload.whatsappNumber).toBe('+56911112222');
    expect(payload.taxPayerRut).toBe('76.123.456-7');
    expect(payload.welcomeDiscountEnabled).toBe(true);
  });
});
