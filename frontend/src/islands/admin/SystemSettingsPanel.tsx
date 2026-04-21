import { useEffect, useMemo, useState } from 'react';
import { Loader2, RefreshCw, Save, ShieldCheck, ShieldX } from 'lucide-react';
import {
  getSystemSettings,
  updateSystemSettings,
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
  smtpHost: string;
  smtpPort: string;
  smtpUsername: string;
  smtpFromEmail: string;
  smtpAuthEnabled: boolean;
  smtpStarttlsEnabled: boolean;
  smtpPassword: string;
  clearSmtpPassword: boolean;
};

function buildFormFromSettings(settings: SystemSettingsDto): FormState {
  return {
    whatsappNumber: settings.whatsappNumber ?? '',
    instagramUrl: settings.instagramUrl ?? '',
    facebookUrl: settings.facebookUrl ?? '',
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

  const smtpPasswordHint = useMemo(() => {
    if (!settings?.smtpPasswordConfigured) return 'Sin password SMTP configurada.';
    if (form.clearSmtpPassword) return 'Se eliminara la password SMTP al guardar.';
    if (form.smtpPassword.trim().length > 0) return 'Se reemplazara la password SMTP actual.';
    return 'Hay una password SMTP guardada (no visible).';
  }, [settings, form.clearSmtpPassword, form.smtpPassword]);

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setFeedback(null);
  }

  async function handleSave() {
    if (!effectiveToken || saving) return;

    const whatsappTrimmed = form.whatsappNumber.trim();
    if (!whatsappTrimmed) {
      setFeedback({ tone: 'error', text: 'El numero de WhatsApp es obligatorio.' });
      return;
    }

    const portTrimmed = form.smtpPort.trim();
    const smtpPort = portTrimmed ? Number(portTrimmed) : undefined;
    if (smtpPort !== undefined && (!Number.isFinite(smtpPort) || smtpPort <= 0 || smtpPort > 65535)) {
      setFeedback({ tone: 'error', text: 'El puerto SMTP debe estar entre 1 y 65535.' });
      return;
    }

    const payload: UpdateSystemSettingsRequest = {
      whatsappNumber: whatsappTrimmed,
      instagramUrl: form.instagramUrl.trim(),
      facebookUrl: form.facebookUrl.trim(),
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

        <div className="mt-3 inline-flex items-center gap-2 rounded-sm border border-pe-black/10 bg-pe-offwhite px-2.5 py-2">
          {settings?.smtpPasswordConfigured && !form.clearSmtpPassword ? (
            <ShieldCheck size={14} className="text-green-700" />
          ) : (
            <ShieldX size={14} className="text-amber-700" />
          )}
          <span className="font-sans text-[0.72rem] text-pe-charcoal/65">{smtpPasswordHint}</span>
        </div>
      </section>

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
