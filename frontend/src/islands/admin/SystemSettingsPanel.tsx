import { useEffect, useMemo, useState } from 'react';
import {
  BellRing,
  CloudCog,
  Loader2,
  Mail,
  MessageCircleMore,
  RefreshCw,
  Save,
  ShieldCheck,
  ShieldX,
} from 'lucide-react';
import {
  getSystemSettings,
  updateSystemSettings,
  type NotificationProvider,
  type SystemSettingsDto,
  type UpdateSystemSettingsRequest,
} from '../../lib/api';
import { readAuthTokenCookie, useAuthStore } from '../../lib/authStore';

type FeedbackState = {
  tone: 'success' | 'error';
  text: string;
};

type FormState = {
  whatsappNumber: string;
  instagramUrl: string;
  facebookUrl: string;
  notificationProvider: NotificationProvider;
  whatsappSimulatedTo: string;
  whatsappSimulatedSender: string;
  whatsappTwilioApiBaseUrl: string;
  whatsappTwilioAccountSid: string;
  whatsappTwilioFrom: string;
  whatsappTwilioToFallback: string;
  whatsappTwilioSenderAlias: string;
  whatsappTwilioAuthToken: string;
  clearWhatsappTwilioAuthToken: boolean;
  sendgridApiBaseUrl: string;
  sendgridFromEmail: string;
  sendgridSenderName: string;
  sendgridToFallback: string;
  sendgridApiKey: string;
  clearSendgridApiKey: boolean;
  smtpHost: string;
  smtpPort: string;
  smtpUsername: string;
  smtpFromEmail: string;
  smtpAuthEnabled: boolean;
  smtpStarttlsEnabled: boolean;
  smtpPassword: string;
  clearSmtpPassword: boolean;
};

type ProviderOption = {
  value: NotificationProvider;
  label: string;
  subtitle: string;
  icon: typeof BellRing;
};

const PROVIDER_OPTIONS: ProviderOption[] = [
  {
    value: 'LOG',
    label: 'Solo log',
    subtitle: 'No envia mensajes, solo registra eventos en backend.',
    icon: BellRing,
  },
  {
    value: 'WHATSAPP_SIMULATED',
    label: 'WhatsApp Simulado',
    subtitle: 'Flujo de pruebas sin proveedor externo.',
    icon: MessageCircleMore,
  },
  {
    value: 'WHATSAPP_TWILIO',
    label: 'WhatsApp Twilio',
    subtitle: 'Mensajeria real por Twilio API.',
    icon: CloudCog,
  },
  {
    value: 'EMAIL_SENDGRID',
    label: 'Email SendGrid',
    subtitle: 'Entrega transaccional via SendGrid.',
    icon: Mail,
  },
  {
    value: 'EMAIL_SMTP',
    label: 'Email SMTP',
    subtitle: 'Servidor SMTP propio o proveedor tradicional.',
    icon: Mail,
  },
];

function buildFormFromSettings(settings: SystemSettingsDto): FormState {
  return {
    whatsappNumber: settings.whatsappNumber ?? '',
    instagramUrl: settings.instagramUrl ?? '',
    facebookUrl: settings.facebookUrl ?? '',
    notificationProvider: settings.notificationProvider ?? 'LOG',
    whatsappSimulatedTo: settings.whatsappSimulatedTo ?? '',
    whatsappSimulatedSender: settings.whatsappSimulatedSender ?? '',
    whatsappTwilioApiBaseUrl: settings.whatsappTwilioApiBaseUrl ?? '',
    whatsappTwilioAccountSid: settings.whatsappTwilioAccountSid ?? '',
    whatsappTwilioFrom: settings.whatsappTwilioFrom ?? '',
    whatsappTwilioToFallback: settings.whatsappTwilioToFallback ?? '',
    whatsappTwilioSenderAlias: settings.whatsappTwilioSenderAlias ?? '',
    whatsappTwilioAuthToken: '',
    clearWhatsappTwilioAuthToken: false,
    sendgridApiBaseUrl: settings.sendgridApiBaseUrl ?? '',
    sendgridFromEmail: settings.sendgridFromEmail ?? '',
    sendgridSenderName: settings.sendgridSenderName ?? '',
    sendgridToFallback: settings.sendgridToFallback ?? '',
    sendgridApiKey: '',
    clearSendgridApiKey: false,
    smtpHost: settings.smtpHost ?? '',
    smtpPort: settings.smtpPort ? String(settings.smtpPort) : '',
    smtpUsername: settings.smtpUsername ?? '',
    smtpFromEmail: settings.smtpFromEmail ?? '',
    smtpAuthEnabled: settings.smtpAuthEnabled ?? true,
    smtpStarttlsEnabled: settings.smtpStarttlsEnabled ?? true,
    smtpPassword: '',
    clearSmtpPassword: false,
  };
}

function formatTimestamp(value?: string) {
  if (!value) return 'Sin registro';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function SecurityHint({
  configured,
  clearFlag,
  newValue,
  emptyText,
  replaceText,
  clearText,
  keepText,
}: {
  configured: boolean;
  clearFlag: boolean;
  newValue: string;
  emptyText: string;
  replaceText: string;
  clearText: string;
  keepText: string;
}) {
  let hint = emptyText;
  if (configured) {
    hint = keepText;
  }
  if (newValue.trim().length > 0) {
    hint = replaceText;
  }
  if (clearFlag) {
    hint = clearText;
  }

  return (
    <div className="mt-3 inline-flex items-center gap-2 rounded-sm border border-pe-black/10 bg-pe-offwhite px-2.5 py-2">
      {configured && !clearFlag ? (
        <ShieldCheck size={14} className="text-green-700" />
      ) : (
        <ShieldX size={14} className="text-amber-700" />
      )}
      <span className="font-sans text-[0.72rem] text-pe-charcoal/65">{hint}</span>
    </div>
  );
}

export default function SystemSettingsPanel() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [settings, setSettings] = useState<SystemSettingsDto | null>(null);
  const [form, setForm] = useState<FormState>({
    whatsappNumber: '',
    instagramUrl: '',
    facebookUrl: '',
    notificationProvider: 'LOG',
    whatsappSimulatedTo: '',
    whatsappSimulatedSender: '',
    whatsappTwilioApiBaseUrl: '',
    whatsappTwilioAccountSid: '',
    whatsappTwilioFrom: '',
    whatsappTwilioToFallback: '',
    whatsappTwilioSenderAlias: '',
    whatsappTwilioAuthToken: '',
    clearWhatsappTwilioAuthToken: false,
    sendgridApiBaseUrl: '',
    sendgridFromEmail: '',
    sendgridSenderName: '',
    sendgridToFallback: '',
    sendgridApiKey: '',
    clearSendgridApiKey: false,
    smtpHost: '',
    smtpPort: '',
    smtpUsername: '',
    smtpFromEmail: '',
    smtpAuthEnabled: true,
    smtpStarttlsEnabled: true,
    smtpPassword: '',
    clearSmtpPassword: false,
  });

  async function loadSettings() {
    if (!effectiveToken) {
      setFeedback({ tone: 'error', text: 'No hay sesion admin activa.' });
      setLoading(false);
      return;
    }

    setLoading(true);
    setFeedback(null);
    try {
      const data = await getSystemSettings(effectiveToken);
      setSettings(data);
      setForm(buildFormFromSettings(data));
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      setFeedback({
        tone: 'error',
        text: text ? `No se pudo cargar la configuracion del sistema: ${text}` : 'No se pudo cargar la configuracion del sistema.',
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadSettings();
  }, [effectiveToken]);

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setFeedback(null);
  }

  const hasProviderRequiringSmtp = form.notificationProvider === 'EMAIL_SMTP';
  const hasProviderRequiringSendgrid = form.notificationProvider === 'EMAIL_SENDGRID';
  const hasProviderRequiringTwilio = form.notificationProvider === 'WHATSAPP_TWILIO';
  const hasProviderSimulated = form.notificationProvider === 'WHATSAPP_SIMULATED';

  async function handleSave() {
    if (!effectiveToken || saving) return;

    const whatsappTrimmed = form.whatsappNumber.trim();
    if (!whatsappTrimmed) {
      setFeedback({ tone: 'error', text: 'El numero de WhatsApp de la tienda es obligatorio.' });
      return;
    }

    if (!form.notificationProvider) {
      setFeedback({ tone: 'error', text: 'Debes seleccionar un proveedor de notificaciones.' });
      return;
    }

    if (hasProviderRequiringTwilio) {
      if (!form.whatsappTwilioAccountSid.trim()) {
        setFeedback({ tone: 'error', text: 'Para Twilio debes indicar Account SID.' });
        return;
      }
      if (!form.whatsappTwilioFrom.trim()) {
        setFeedback({ tone: 'error', text: 'Para Twilio debes indicar el numero de origen (From).' });
        return;
      }
    }

    if (hasProviderRequiringSendgrid && !form.sendgridFromEmail.trim()) {
      setFeedback({ tone: 'error', text: 'Para SendGrid debes indicar correo remitente.' });
      return;
    }

    const portTrimmed = form.smtpPort.trim();
    const smtpPort = portTrimmed ? Number(portTrimmed) : undefined;
    if (smtpPort !== undefined && (!Number.isFinite(smtpPort) || smtpPort <= 0 || smtpPort > 65535)) {
      setFeedback({ tone: 'error', text: 'El puerto SMTP debe estar entre 1 y 65535.' });
      return;
    }

    if (hasProviderRequiringSmtp) {
      if (!form.smtpHost.trim()) {
        setFeedback({ tone: 'error', text: 'Para SMTP debes indicar host.' });
        return;
      }
      if (smtpPort === undefined) {
        setFeedback({ tone: 'error', text: 'Para SMTP debes indicar puerto.' });
        return;
      }
      if (!form.smtpFromEmail.trim()) {
        setFeedback({ tone: 'error', text: 'Para SMTP debes indicar correo remitente.' });
        return;
      }
    }

    const payload: UpdateSystemSettingsRequest = {
      whatsappNumber: whatsappTrimmed,
      instagramUrl: form.instagramUrl.trim(),
      facebookUrl: form.facebookUrl.trim(),
      notificationProvider: form.notificationProvider,
      whatsappSimulatedTo: form.whatsappSimulatedTo.trim(),
      whatsappSimulatedSender: form.whatsappSimulatedSender.trim(),
      whatsappTwilioApiBaseUrl: form.whatsappTwilioApiBaseUrl.trim(),
      whatsappTwilioAccountSid: form.whatsappTwilioAccountSid.trim(),
      whatsappTwilioFrom: form.whatsappTwilioFrom.trim(),
      whatsappTwilioToFallback: form.whatsappTwilioToFallback.trim(),
      whatsappTwilioSenderAlias: form.whatsappTwilioSenderAlias.trim(),
      whatsappTwilioAuthToken: form.whatsappTwilioAuthToken.trim(),
      clearWhatsappTwilioAuthToken: form.clearWhatsappTwilioAuthToken,
      sendgridApiBaseUrl: form.sendgridApiBaseUrl.trim(),
      sendgridFromEmail: form.sendgridFromEmail.trim(),
      sendgridSenderName: form.sendgridSenderName.trim(),
      sendgridToFallback: form.sendgridToFallback.trim(),
      sendgridApiKey: form.sendgridApiKey.trim(),
      clearSendgridApiKey: form.clearSendgridApiKey,
      smtpHost: form.smtpHost.trim(),
      smtpPort,
      smtpUsername: form.smtpUsername.trim(),
      smtpFromEmail: form.smtpFromEmail.trim(),
      smtpPassword: form.smtpPassword.trim(),
      clearSmtpPassword: form.clearSmtpPassword,
      smtpAuthEnabled: form.smtpAuthEnabled,
      smtpStarttlsEnabled: form.smtpStarttlsEnabled,
    };

    setSaving(true);
    setFeedback(null);
    try {
      const saved = await updateSystemSettings(payload, effectiveToken);
      setSettings(saved);
      setForm(buildFormFromSettings(saved));
      setFeedback({ tone: 'success', text: 'Configuracion guardada correctamente.' });
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      setFeedback({
        tone: 'error',
        text: text
          ? `No se pudo guardar la configuracion: ${text}`
          : 'No se pudo guardar la configuracion. Revisa los datos e intenta de nuevo.',
      });
    } finally {
      setSaving(false);
    }
  }

  const selectedProvider = useMemo(
    () => PROVIDER_OPTIONS.find((option) => option.value === form.notificationProvider),
    [form.notificationProvider]
  );

  if (loading) {
    return (
      <div className="border border-pe-black/10 bg-pe-white p-6 flex items-center gap-2 text-pe-charcoal/65">
        <Loader2 size={15} className="animate-spin" />
        <span className="font-sans text-sm">Cargando configuracion...</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="font-sans text-[0.74rem] text-pe-charcoal/55">
          Ultima actualizacion: {formatTimestamp(settings?.updatedAt)}{settings?.updatedBy ? ` por ${settings.updatedBy}` : ''}
        </div>
        <button
          type="button"
          onClick={() => void loadSettings()}
          disabled={saving}
          className="inline-flex items-center gap-1 border border-pe-black/15 px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-charcoal/70 hover:bg-pe-cream disabled:opacity-50"
        >
          <RefreshCw size={12} />
          Recargar
        </button>
      </div>

      {feedback && (
        <div
          className={[
            'border px-3 py-2 font-sans text-[0.74rem]',
            feedback.tone === 'success'
              ? 'border-green-200 bg-green-50 text-green-700'
              : 'border-red-200 bg-red-50 text-red-700',
          ].join(' ')}
        >
          {feedback.text}
        </div>
      )}

      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Canales tienda</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
          Estos valores alimentan el boton flotante de WhatsApp y los enlaces sociales del storefront.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 md:col-span-2">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Numero WhatsApp</span>
            <input
              type="text"
              value={form.whatsappNumber}
              onChange={(e) => updateField('whatsappNumber', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
              placeholder="+56912345678"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">URL Instagram</span>
            <input
              type="url"
              value={form.instagramUrl}
              onChange={(e) => updateField('instagramUrl', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
              placeholder="https://instagram.com/tu_cuenta"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">URL Facebook</span>
            <input
              type="url"
              value={form.facebookUrl}
              onChange={(e) => updateField('facebookUrl', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
              placeholder="https://facebook.com/tu_pagina"
            />
          </label>
        </div>
      </section>

      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Proveedor de notificaciones</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
          Selecciona el canal transaccional (pedidos, pagos y envios). La configuracion queda guardada en la plataforma.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-2.5">
          {PROVIDER_OPTIONS.map((option) => {
            const Icon = option.icon;
            const isActive = form.notificationProvider === option.value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => updateField('notificationProvider', option.value)}
                className={[
                  'text-left border px-3 py-3 transition',
                  isActive
                    ? 'border-pe-rose bg-pe-rose/10'
                    : 'border-pe-black/10 hover:border-pe-black/25 hover:bg-pe-offwhite',
                ].join(' ')}
              >
                <div className="inline-flex items-center gap-2">
                  <Icon size={14} className={isActive ? 'text-pe-rose' : 'text-pe-charcoal/70'} />
                  <span className="font-sans text-[0.72rem] uppercase tracking-[0.14em] text-pe-charcoal">{option.label}</span>
                </div>
                <p className="mt-2 font-sans text-[0.7rem] text-pe-charcoal/60 leading-relaxed">{option.subtitle}</p>
              </button>
            );
          })}
        </div>

        <div className="mt-3 rounded-sm border border-pe-black/10 bg-pe-offwhite px-3 py-2">
          <span className="font-sans text-[0.72rem] text-pe-charcoal/70">
            Activo ahora: <strong>{selectedProvider?.label ?? form.notificationProvider}</strong>
          </span>
        </div>
      </section>

      {hasProviderSimulated && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">WhatsApp Simulado</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            Este modo no envia mensajes reales. Deja trazas en logs para pruebas.
          </p>
          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Destino simulado</span>
              <input
                type="text"
                value={form.whatsappSimulatedTo}
                onChange={(e) => updateField('whatsappSimulatedTo', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="+56900000000"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Alias remitente</span>
              <input
                type="text"
                value={form.whatsappSimulatedSender}
                onChange={(e) => updateField('whatsappSimulatedSender', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Pilar Estilo"
              />
            </label>
          </div>
        </section>
      )}

      {hasProviderRequiringTwilio && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">WhatsApp Twilio</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            Configura credenciales y numeros en formato internacional.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">API base URL</span>
              <input
                type="url"
                value={form.whatsappTwilioApiBaseUrl}
                onChange={(e) => updateField('whatsappTwilioApiBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="https://api.twilio.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Account SID</span>
              <input
                type="text"
                value={form.whatsappTwilioAccountSid}
                onChange={(e) => updateField('whatsappTwilioAccountSid', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="ACxxxxxxxxxxxxxxxx"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">WhatsApp From</span>
              <input
                type="text"
                value={form.whatsappTwilioFrom}
                onChange={(e) => updateField('whatsappTwilioFrom', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="whatsapp:+14155238886"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Fallback destino</span>
              <input
                type="text"
                value={form.whatsappTwilioToFallback}
                onChange={(e) => updateField('whatsappTwilioToFallback', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="+56900000000"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Alias remitente</span>
              <input
                type="text"
                value={form.whatsappTwilioSenderAlias}
                onChange={(e) => updateField('whatsappTwilioSenderAlias', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Pilar Estilo"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nuevo Auth Token</span>
              <input
                type="password"
                value={form.whatsappTwilioAuthToken}
                onChange={(e) => updateField('whatsappTwilioAuthToken', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.clearWhatsappTwilioAuthToken}
                onChange={(e) => updateField('clearWhatsappTwilioAuthToken', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Limpiar Auth Token guardado
            </label>
          </div>

          <SecurityHint
            configured={Boolean(settings?.whatsappTwilioAuthTokenConfigured)}
            clearFlag={form.clearWhatsappTwilioAuthToken}
            newValue={form.whatsappTwilioAuthToken}
            emptyText="Sin token Twilio configurado."
            keepText="Hay un token Twilio guardado (no visible)."
            replaceText="Se reemplazara el token Twilio actual."
            clearText="Se eliminara el token Twilio al guardar."
          />
        </section>
      )}

      {hasProviderRequiringSendgrid && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">Correo SendGrid</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            Usa API key cifrada y correo remitente para notificaciones transaccionales.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">API base URL</span>
              <input
                type="url"
                value={form.sendgridApiBaseUrl}
                onChange={(e) => updateField('sendgridApiBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="https://api.sendgrid.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Correo remitente</span>
              <input
                type="email"
                value={form.sendgridFromEmail}
                onChange={(e) => updateField('sendgridFromEmail', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="ventas@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nombre remitente</span>
              <input
                type="text"
                value={form.sendgridSenderName}
                onChange={(e) => updateField('sendgridSenderName', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Pilar Estilo"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Fallback destino</span>
              <input
                type="email"
                value={form.sendgridToFallback}
                onChange={(e) => updateField('sendgridToFallback', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="alerts@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nueva API key</span>
              <input
                type="password"
                value={form.sendgridApiKey}
                onChange={(e) => updateField('sendgridApiKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Deja vacio para mantener la actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.clearSendgridApiKey}
                onChange={(e) => updateField('clearSendgridApiKey', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Limpiar API key guardada
            </label>
          </div>

          <SecurityHint
            configured={Boolean(settings?.sendgridApiKeyConfigured)}
            clearFlag={form.clearSendgridApiKey}
            newValue={form.sendgridApiKey}
            emptyText="Sin API key SendGrid configurada."
            keepText="Hay una API key SendGrid guardada (no visible)."
            replaceText="Se reemplazara la API key SendGrid actual."
            clearText="Se eliminara la API key SendGrid al guardar."
          />
        </section>
      )}

      {hasProviderRequiringSmtp && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">Correo SMTP</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            La password se guarda cifrada en base de datos y no se muestra en texto plano.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Host SMTP</span>
              <input
                type="text"
                value={form.smtpHost}
                onChange={(e) => updateField('smtpHost', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="smtp.gmail.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Puerto SMTP</span>
              <input
                type="text"
                inputMode="numeric"
                value={form.smtpPort}
                onChange={(e) => updateField('smtpPort', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="587"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Usuario SMTP</span>
              <input
                type="text"
                value={form.smtpUsername}
                onChange={(e) => updateField('smtpUsername', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="usuario_smtp"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Correo remitente</span>
              <input
                type="email"
                value={form.smtpFromEmail}
                onChange={(e) => updateField('smtpFromEmail', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="ventas@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nueva password SMTP</span>
              <input
                type="password"
                value={form.smtpPassword}
                onChange={(e) => updateField('smtpPassword', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Deja vacio para mantener la actual"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.smtpAuthEnabled}
                onChange={(e) => updateField('smtpAuthEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              SMTP auth habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.smtpStarttlsEnabled}
                onChange={(e) => updateField('smtpStarttlsEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              STARTTLS habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.clearSmtpPassword}
                onChange={(e) => updateField('clearSmtpPassword', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Limpiar password SMTP guardada
            </label>
          </div>

          <SecurityHint
            configured={Boolean(settings?.smtpPasswordConfigured)}
            clearFlag={form.clearSmtpPassword}
            newValue={form.smtpPassword}
            emptyText="Sin password SMTP configurada."
            keepText="Hay una password SMTP guardada (no visible)."
            replaceText="Se reemplazara la password SMTP actual."
            clearText="Se eliminara la password SMTP al guardar."
          />
        </section>
      )}

      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={saving}
          className="inline-flex items-center gap-2 bg-pe-black px-4 py-2.5 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-offwhite hover:bg-pe-charcoal disabled:opacity-50"
        >
          {saving ? <Loader2 size={13} className="animate-spin" /> : <Save size={13} />}
          Guardar configuracion
        </button>
      </div>
    </div>
  );
}
