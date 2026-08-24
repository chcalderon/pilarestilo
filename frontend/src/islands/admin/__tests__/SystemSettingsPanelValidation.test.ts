import { describe, expect, it } from 'vitest';
import {
  validateSettingsForm,
  buildFormFromSettings,
  type FormState,
  type SettingsValidationFlags,
} from '../SystemSettingsPanel';
import type { SystemSettingsDto } from '@/lib/api';

/**
 * Unit tests for handleSave's extracted validation cascade (S3776, complexity 63) -- it had none.
 * A pure function taking the form, its derived flags, and the parsed numeric fields needs no
 * component rendering (no auth store, no API mocks), so this is a plain unit test.
 */

function baseSettingsDto(): SystemSettingsDto {
  return {
    whatsappNumber: '+56911111111',
    paymentMethodBankTransferEnabled: true,
    paymentMethodGatewayEnabled: true,
    paymentGatewayProviders: ['MERCADO_PAGO'],
    paymentGatewayMpAccessTokenConfigured: false,
    paymentGatewayMpWebhookTokenConfigured: false,
    mediaStorageProvider: 'LOCAL',
    mediaS3PathStyleEnabled: false,
    mediaS3SecretKeyConfigured: false,
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
    taxDocumentRequiredBeforeDispatch: true,
    taxDocumentProvider: 'MANUAL',
    welcomeDiscountEnabled: false,
    welcomeDiscountType: 'PERCENTAGE',
    welcomeDiscountValue: 10,
    welcomeDiscountMinOrderAmount: 0,
    welcomeDiscountRequiresMarketing: true,
  } as SystemSettingsDto;
}

function baseForm(overrides: Partial<FormState> = {}): FormState {
  return {
    ...buildFormFromSettings(baseSettingsDto()),
    bankTransferAccountHolder: 'Ana Perez',
    bankTransferContactEmail: 'ana@pilarestilo.com',
    bankTransferAccountNumber: '1234567',
    bankTransferBankName: 'Banco de Chile',
    bankTransferAccountType: 'Cuenta Corriente',
    ...overrides,
  };
}

function baseFlags(overrides: Partial<SettingsValidationFlags> = {}): SettingsValidationFlags {
  return {
    hasProviderRequiringSmtp: false,
    hasProviderRequiringSendgrid: false,
    hasProviderRequiringTwilio: false,
    hasProviderN8n: false,
    hasS3CompatibleStorage: false,
    hasMercadoPagoSelected: true,
    isBancoEstadoSelected: false,
    ...overrides,
  };
}

function baseNumbers() {
  return { smtpPort: undefined, aiInferBasePrice: undefined, aiInferListMultiplier: undefined };
}

describe('validateSettingsForm', () => {
  it('accepts a fully valid form', () => {
    expect(validateSettingsForm(baseForm(), baseFlags(), baseNumbers())).toBeNull();
  });

  it('requires a WhatsApp number', () => {
    expect(validateSettingsForm(baseForm({ whatsappNumber: ' ' }), baseFlags(), baseNumbers()))
      .toMatch(/whatsapp.*obligatorio/i);
  });

  it('requires at least one notification channel', () => {
    expect(validateSettingsForm(baseForm({ notificationProviders: [] }), baseFlags(), baseNumbers()))
      .toMatch(/canal de notificaciones/i);
  });

  it('requires a media storage provider', () => {
    expect(validateSettingsForm(baseForm({ mediaStorageProvider: '' as never }), baseFlags(), baseNumbers()))
      .toMatch(/almacenamiento de imagenes/i);
  });

  it('requires at least one payment method enabled', () => {
    const form = baseForm({ paymentMethodBankTransferEnabled: false, paymentMethodGatewayEnabled: false });
    expect(validateSettingsForm(form, baseFlags(), baseNumbers())).toMatch(/medio de pago/i);
  });

  describe('bank transfer fields', () => {
    it('requires holder, email, account number, bank and account type when enabled', () => {
      expect(validateSettingsForm(baseForm({ bankTransferAccountHolder: '' }), baseFlags(), baseNumbers())).toMatch(/titular/i);
      expect(validateSettingsForm(baseForm({ bankTransferContactEmail: '' }), baseFlags(), baseNumbers())).toMatch(/correo de contacto/i);
      expect(validateSettingsForm(baseForm({ bankTransferAccountNumber: '' }), baseFlags(), baseNumbers())).toMatch(/numero de cuenta/i);
      expect(validateSettingsForm(baseForm({ bankTransferBankName: '' }), baseFlags(), baseNumbers())).toMatch(/indicar banco/i);
      expect(validateSettingsForm(baseForm({ bankTransferAccountType: '' }), baseFlags(), baseNumbers())).toMatch(/tipo de cuenta/i);
    });

    it('refuses Cuenta RUT unless BancoEstado is selected', () => {
      const form = baseForm({ bankTransferAccountType: 'Cuenta RUT' });
      expect(validateSettingsForm(form, baseFlags({ isBancoEstadoSelected: false }), baseNumbers())).toMatch(/Cuenta RUT/);
      expect(validateSettingsForm(form, baseFlags({ isBancoEstadoSelected: true }), baseNumbers())).toBeNull();
    });

    it('skips all bank transfer checks when it is disabled', () => {
      const form = baseForm({
        paymentMethodBankTransferEnabled: false,
        bankTransferAccountHolder: '',
        bankTransferContactEmail: '',
      });
      expect(validateSettingsForm(form, baseFlags(), baseNumbers())).toBeNull();
    });
  });

  describe('payment gateway fields', () => {
    it('requires at least one provider when the gateway is enabled', () => {
      const form = baseForm({ paymentGatewayProviders: [] });
      expect(validateSettingsForm(form, baseFlags(), baseNumbers())).toMatch(/al menos un proveedor/i);
    });

    it('requires the Mercado Pago API base URL when it is the selected provider', () => {
      const form = baseForm({ paymentGatewayMpApiBaseUrl: '' });
      expect(validateSettingsForm(form, baseFlags({ hasMercadoPagoSelected: true }), baseNumbers())).toMatch(/Mercado Pago/);
    });

    it('skips gateway checks entirely when it is disabled', () => {
      const form = baseForm({ paymentGatewayProviders: [], paymentMethodGatewayEnabled: false });
      expect(validateSettingsForm(form, baseFlags(), baseNumbers())).toBeNull();
    });
  });

  it('requires a bucket for S3-compatible media storage', () => {
    const form = baseForm({ mediaS3Bucket: '' });
    expect(validateSettingsForm(form, baseFlags({ hasS3CompatibleStorage: true }), baseNumbers())).toMatch(/bucket/i);
  });

  describe('Twilio fields', () => {
    it('requires Account SID and From number', () => {
      const flags = baseFlags({ hasProviderRequiringTwilio: true });
      expect(validateSettingsForm(baseForm({ whatsappTwilioAccountSid: '' }), flags, baseNumbers())).toMatch(/Account SID/);
      expect(validateSettingsForm(
        baseForm({ whatsappTwilioAccountSid: 'AC123', whatsappTwilioFrom: '' }), flags, baseNumbers()
      )).toMatch(/From/);
    });

    it('is skipped when Twilio is not among the selected providers', () => {
      const form = baseForm({ whatsappTwilioAccountSid: '', whatsappTwilioFrom: '' });
      expect(validateSettingsForm(form, baseFlags({ hasProviderRequiringTwilio: false }), baseNumbers())).toBeNull();
    });
  });

  it('requires a SendGrid sender email', () => {
    const flags = baseFlags({ hasProviderRequiringSendgrid: true });
    expect(validateSettingsForm(baseForm({ sendgridFromEmail: '' }), flags, baseNumbers())).toMatch(/SendGrid/);
  });

  describe('numeric ranges', () => {
    it('rejects an SMTP port outside 1-65535', () => {
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), smtpPort: 0 })).toMatch(/puerto SMTP/i);
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), smtpPort: 70000 })).toMatch(/puerto SMTP/i);
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), smtpPort: 587 })).toBeNull();
    });

    it('rejects an AI base price under 1000', () => {
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), aiInferBasePrice: 500 })).toMatch(/precio base sugerido/i);
    });

    it('rejects an AI list multiplier outside 1-5', () => {
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), aiInferListMultiplier: 0.5 })).toMatch(/multiplicador/i);
      expect(validateSettingsForm(baseForm(), baseFlags(), { ...baseNumbers(), aiInferListMultiplier: 6 })).toMatch(/multiplicador/i);
    });
  });

  it('requires a default AI brand', () => {
    expect(validateSettingsForm(baseForm({ productAiInferDefaultBrand: ' ' }), baseFlags(), baseNumbers())).toMatch(/marca por defecto/i);
  });

  describe('SMTP fields', () => {
    it('requires host, port and sender email', () => {
      const flags = baseFlags({ hasProviderRequiringSmtp: true });
      expect(validateSettingsForm(baseForm({ smtpHost: '' }), flags, baseNumbers())).toMatch(/SMTP debes indicar host/i);
      expect(validateSettingsForm(baseForm({ smtpHost: 'smtp.example.com' }), flags, baseNumbers())).toMatch(/SMTP debes indicar puerto/i);
      expect(validateSettingsForm(
        baseForm({ smtpHost: 'smtp.example.com', smtpFromEmail: '' }), flags, { ...baseNumbers(), smtpPort: 587 }
      )).toMatch(/correo remitente/i);
    });

    it('is skipped when SMTP is not among the selected providers', () => {
      expect(validateSettingsForm(baseForm({ smtpHost: '' }), baseFlags({ hasProviderRequiringSmtp: false }), baseNumbers())).toBeNull();
    });
  });

  describe('n8n fields', () => {
    it('requires the webhook url to start with http:// or https://, when non-empty', () => {
      const flags = baseFlags({ hasProviderN8n: true });
      expect(validateSettingsForm(baseForm({ n8nWebhookUrl: 'ftp://evil' }), flags, baseNumbers())).toMatch(/http:\/\/ o https:\/\//);
      expect(validateSettingsForm(baseForm({ n8nWebhookUrl: '' }), flags, baseNumbers())).toBeNull();
    });

    it('requires a token header name', () => {
      const flags = baseFlags({ hasProviderN8n: true });
      expect(validateSettingsForm(baseForm({ n8nTokenHeaderName: '' }), flags, baseNumbers())).toMatch(/header para el token/i);
    });

    it('is skipped when n8n is not among the selected providers', () => {
      const form = baseForm({ n8nWebhookUrl: 'not-a-url', n8nTokenHeaderName: '' });
      expect(validateSettingsForm(form, baseFlags({ hasProviderN8n: false }), baseNumbers())).toBeNull();
    });
  });
});
