import { useState, useEffect } from 'react';
import { Gift, Loader2, Check } from 'lucide-react';
import { getSystemSettings, updateSystemSettings, type SystemSettingsDto } from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useToast, Toaster } from './Toast';

const INPUT_CLASS =
  'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal ' +
  'focus:outline-hidden focus:border-pe-rose/50 transition-colors w-full ' +
  'disabled:opacity-50 disabled:cursor-not-allowed';

const LABEL_CLASS = 'font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted';

/** Fixed by the owner's own call, not exposed as a setting — see IssueWelcomeDiscountUseCase. */
const VALIDITY_DAYS = 30;

function summarize(enabled: boolean, type: string, value: number, minOrderAmount: number, requiresMarketing: boolean) {
  if (!enabled) return 'El cupón de bienvenida está apagado. Las cuentas nuevas no reciben ningún código.';
  const amount = type === 'PERCENTAGE' ? `${value}%` : `$${value.toLocaleString('es-CL')}`;
  const minOrder = minOrderAmount > 0
    ? ` en compras sobre $${minOrderAmount.toLocaleString('es-CL')}`
    : '';
  const who = requiresMarketing
    ? 'Cada cuenta nueva que acepta recibir novedades por correo recibe'
    : 'Cada cuenta nueva recibe';
  return `${who} un código único de ${amount}${minOrder}, válido ${VALIDITY_DAYS} días.`;
}

export default function WelcomeDiscountPanel() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const { toasts, show, dismiss } = useToast();

  const [settings, setSettings] = useState<SystemSettingsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [enabled, setEnabled] = useState(false);
  const [type, setType] = useState<'PERCENTAGE' | 'FIXED'>('PERCENTAGE');
  const [value, setValue] = useState(10);
  const [minOrderAmount, setMinOrderAmount] = useState(0);
  const [requiresMarketing, setRequiresMarketing] = useState(true);

  useEffect(() => {
    if (!effectiveToken) return;
    (async () => {
      setLoading(true);
      try {
        const data = await getSystemSettings(effectiveToken);
        setSettings(data);
        setEnabled(data.welcomeDiscountEnabled);
        setType(data.welcomeDiscountType);
        setValue(data.welcomeDiscountValue);
        setMinOrderAmount(data.welcomeDiscountMinOrderAmount);
        setRequiresMarketing(data.welcomeDiscountRequiresMarketing);
      } finally {
        setLoading(false);
      }
    })();
  }, [effectiveToken]);

  async function handleSave() {
    if (!effectiveToken || !settings) return;
    setSaving(true);
    try {
      const updated = await updateSystemSettings({
        // The endpoint replaces the whole row — every other field travels back untouched.
        whatsappNumber: settings.whatsappNumber,
        instagramUrl: settings.instagramUrl ?? undefined,
        facebookUrl: settings.facebookUrl ?? undefined,
        bankTransferAccountHolder: settings.bankTransferAccountHolder ?? undefined,
        bankTransferContactEmail: settings.bankTransferContactEmail ?? undefined,
        bankTransferAccountNumber: settings.bankTransferAccountNumber ?? undefined,
        bankTransferBankName: settings.bankTransferBankName ?? undefined,
        bankTransferAccountType: settings.bankTransferAccountType ?? undefined,
        paymentMethodBankTransferEnabled: settings.paymentMethodBankTransferEnabled,
        paymentMethodGatewayEnabled: settings.paymentMethodGatewayEnabled,
        paymentGatewayProviders: settings.paymentGatewayProviders,
        paymentGatewayMpApiBaseUrl: settings.paymentGatewayMpApiBaseUrl ?? undefined,
        paymentGatewayMpSuccessUrl: settings.paymentGatewayMpSuccessUrl ?? undefined,
        paymentGatewayMpPendingUrl: settings.paymentGatewayMpPendingUrl ?? undefined,
        paymentGatewayMpFailureUrl: settings.paymentGatewayMpFailureUrl ?? undefined,
        paymentGatewayMpNotificationUrl: settings.paymentGatewayMpNotificationUrl ?? undefined,
        mediaStorageProvider: settings.mediaStorageProvider,
        mediaS3Endpoint: settings.mediaS3Endpoint ?? undefined,
        mediaS3Region: settings.mediaS3Region ?? undefined,
        mediaS3Bucket: settings.mediaS3Bucket ?? undefined,
        mediaS3AccessKeyId: settings.mediaS3AccessKeyId ?? undefined,
        mediaS3PathStyleEnabled: settings.mediaS3PathStyleEnabled,
        mediaS3PublicBaseUrl: settings.mediaS3PublicBaseUrl ?? undefined,
        notificationProviders: settings.notificationProviders,
        n8nWebhookUrl: settings.n8nWebhookUrl ?? undefined,
        n8nTokenHeaderName: settings.n8nTokenHeaderName ?? undefined,
        whatsappSimulatedTo: settings.whatsappSimulatedTo ?? undefined,
        whatsappSimulatedSender: settings.whatsappSimulatedSender ?? undefined,
        whatsappTwilioApiBaseUrl: settings.whatsappTwilioApiBaseUrl ?? undefined,
        whatsappTwilioAccountSid: settings.whatsappTwilioAccountSid ?? undefined,
        whatsappTwilioFrom: settings.whatsappTwilioFrom ?? undefined,
        whatsappTwilioToFallback: settings.whatsappTwilioToFallback ?? undefined,
        whatsappTwilioSenderAlias: settings.whatsappTwilioSenderAlias ?? undefined,
        sendgridApiBaseUrl: settings.sendgridApiBaseUrl ?? undefined,
        sendgridFromEmail: settings.sendgridFromEmail ?? undefined,
        sendgridSenderName: settings.sendgridSenderName ?? undefined,
        sendgridToFallback: settings.sendgridToFallback ?? undefined,
        productAiInferDefaultBrand: settings.productAiInferDefaultBrand ?? undefined,
        productAiInferDefaultCondition: settings.productAiInferDefaultCondition ?? undefined,
        productAiInferBasePrice: settings.productAiInferBasePrice ?? undefined,
        productAiInferListPriceMultiplier: settings.productAiInferListPriceMultiplier ?? undefined,
        smtpHost: settings.smtpHost ?? undefined,
        smtpPort: settings.smtpPort ?? undefined,
        smtpUsername: settings.smtpUsername ?? undefined,
        smtpFromEmail: settings.smtpFromEmail ?? undefined,
        smtpAuthEnabled: settings.smtpAuthEnabled,
        smtpStarttlsEnabled: settings.smtpStarttlsEnabled,
        shippingZonesJson: settings.shippingZonesJson ?? undefined,
        shippingCouriersJson: settings.shippingCouriersJson ?? undefined,
        shippingPaymentMode: settings.shippingPaymentMode,
        bankTransferAutoCancelEnabled: settings.bankTransferAutoCancelEnabled,
        bankTransferAutoCancelTimeoutMinutes: settings.bankTransferAutoCancelTimeoutMinutes,
        bankTransferAutoCancelCron: settings.bankTransferAutoCancelCron,
        taxPayerRut: settings.taxPayerRut,
        taxBusinessName: settings.taxBusinessName,
        taxBusinessActivity: settings.taxBusinessActivity,
        taxActecoCode: settings.taxActecoCode,
        taxAddress: settings.taxAddress,
        taxCommune: settings.taxCommune,
        taxCity: settings.taxCity,
        taxVatRate: settings.taxVatRate,
        taxDocumentRequiredBeforeDispatch: settings.taxDocumentRequiredBeforeDispatch,
        taxDocumentProvider: settings.taxDocumentProvider,
        // The five fields this panel actually changes.
        welcomeDiscountEnabled: enabled,
        welcomeDiscountType: type,
        welcomeDiscountValue: value,
        welcomeDiscountMinOrderAmount: minOrderAmount,
        welcomeDiscountRequiresMarketing: requiresMarketing,
      }, effectiveToken);
      setSettings(updated);
      show('success', 'Cupón de bienvenida actualizado.');
    } catch (e: unknown) {
      show('error', e instanceof Error ? e.message : 'Error al guardar.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="mb-4 flex justify-center py-8 bg-pe-white border border-pe-black/6 shadow-xs">
        <Loader2 size={20} className="animate-spin text-pe-rose-ink" />
      </div>
    );
  }

  return (
    <div className="mb-4 bg-pe-white border border-pe-black/6 shadow-xs p-4">
      <Toaster toasts={toasts} dismiss={dismiss} />

      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2">
          <Gift size={16} className="text-pe-rose-ink" />
          <p className="font-sans text-[0.72rem] uppercase tracking-wider text-pe-charcoal">
            Cupón de bienvenida
          </p>
        </div>
        <label className="inline-flex items-center gap-2 cursor-pointer shrink-0">
          <span className="font-sans text-[0.68rem] uppercase tracking-wider text-pe-muted">
            {enabled ? 'Activo' : 'Apagado'}
          </span>
          <span className="relative inline-flex h-5 w-9 items-center">
            <input
              type="checkbox"
              className="peer sr-only"
              checked={enabled}
              onChange={e => setEnabled(e.target.checked)}
              aria-label="Activar cupón de bienvenida"
            />
            <span className="absolute inset-0 rounded-full bg-pe-black/15 peer-checked:bg-pe-rose-action transition-colors" />
            <span className="absolute left-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform peer-checked:translate-x-4" />
          </span>
        </label>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="flex flex-col gap-0.5">
          <label htmlFor="welcome-discount-type" className={LABEL_CLASS}>Tipo</label>
          <select
            id="welcome-discount-type"
            className={INPUT_CLASS}
            value={type}
            disabled={!enabled}
            onChange={e => setType(e.target.value as 'PERCENTAGE' | 'FIXED')}
          >
            <option value="PERCENTAGE">Porcentaje (%)</option>
            <option value="FIXED">Monto fijo</option>
          </select>
        </div>

        <div className="flex flex-col gap-0.5">
          <label htmlFor="welcome-discount-value" className={LABEL_CLASS}>
            Valor {type === 'PERCENTAGE' ? '(%)' : '($)'}
          </label>
          <input
            id="welcome-discount-value"
            type="number"
            min="0.01"
            max={type === 'PERCENTAGE' ? 100 : undefined}
            step="0.01"
            className={INPUT_CLASS}
            value={value}
            disabled={!enabled}
            onChange={e => setValue(Number(e.target.value))}
          />
        </div>

        <div className="flex flex-col gap-0.5">
          <label htmlFor="welcome-discount-min-order" className={LABEL_CLASS}>Monto mínimo</label>
          <input
            id="welcome-discount-min-order"
            type="number"
            min="0"
            className={INPUT_CLASS}
            value={minOrderAmount}
            disabled={!enabled}
            onChange={e => setMinOrderAmount(Number(e.target.value))}
          />
        </div>

        <div className="flex flex-col gap-0.5">
          <span className={LABEL_CLASS}>Vigencia</span>
          <p className="font-sans text-[0.78rem] text-pe-charcoal px-2 py-1.5 border border-transparent">
            {VALIDITY_DAYS} días desde el registro
          </p>
        </div>
      </div>

      <label className="mt-3 flex items-center gap-2 cursor-pointer w-fit">
        <input
          type="checkbox"
          className="w-4 h-4 accent-pe-rose disabled:opacity-50"
          checked={requiresMarketing}
          disabled={!enabled}
          onChange={e => setRequiresMarketing(e.target.checked)}
        />
        <span className="font-sans text-[0.74rem] text-pe-charcoal">
          Solo para quienes aceptan recibir novedades por correo
        </span>
      </label>

      <p className="mt-3 font-sans text-[0.72rem] leading-relaxed text-pe-muted">
        {summarize(enabled, type, value, minOrderAmount, requiresMarketing)}
      </p>

      <div className="mt-3">
        <button
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-1.5 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-4 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50"
        >
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>
      </div>
    </div>
  );
}
