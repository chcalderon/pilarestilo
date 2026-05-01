import { useEffect, useMemo, useState } from 'react';
import {
  BellRing,
  Database,
  CloudCog,
  HardDrive,
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
  migrateCategoryImages,
  type MediaStorageProvider,
  type PaymentGatewayProvider,
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
  bankTransferAccountHolder: string;
  bankTransferContactEmail: string;
  bankTransferAccountNumber: string;
  bankTransferBankName: string;
  bankTransferAccountType: string;
  paymentMethodBankTransferEnabled: boolean;
  paymentMethodGatewayEnabled: boolean;
  paymentGatewayProviders: PaymentGatewayProvider[];
  paymentGatewayMpApiBaseUrl: string;
  paymentGatewayMpSuccessUrl: string;
  paymentGatewayMpPendingUrl: string;
  paymentGatewayMpFailureUrl: string;
  paymentGatewayMpNotificationUrl: string;
  paymentGatewayMpAccessToken: string;
  clearPaymentGatewayMpAccessToken: boolean;
  paymentGatewayMpWebhookToken: string;
  clearPaymentGatewayMpWebhookToken: boolean;
  mediaStorageProvider: MediaStorageProvider;
  mediaS3Endpoint: string;
  mediaS3Region: string;
  mediaS3Bucket: string;
  mediaS3AccessKeyId: string;
  mediaS3SecretKey: string;
  clearMediaS3SecretKey: boolean;
  mediaS3PathStyleEnabled: boolean;
  mediaS3PublicBaseUrl: string;
  notificationProvider: NotificationProvider;
  n8nWebhookUrl: string;
  n8nTokenHeaderName: string;
  n8nApiKey: string;
  clearN8nApiKey: boolean;
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
  productAiInferDefaultBrand: string;
  productAiInferDefaultCondition: 'NEW' | 'USED';
  productAiInferBasePrice: string;
  productAiInferListPriceMultiplier: string;
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

type MediaStorageOption = {
  value: MediaStorageProvider;
  label: string;
  subtitle: string;
  icon: typeof HardDrive;
};

type PaymentGatewayProviderOption = {
  value: PaymentGatewayProvider;
  label: string;
  subtitle: string;
};

type SettingsSubmenuTab = 'store' | 'payments' | 'media' | 'notifications';

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
  {
    value: 'N8N_WEBHOOK',
    label: 'N8N Webhook',
    subtitle: 'Delega notificaciones a flujos n8n externos por webhook.',
    icon: CloudCog,
  },
];

const MEDIA_STORAGE_OPTIONS: MediaStorageOption[] = [
  {
    value: 'LOCAL',
    label: 'Local',
    subtitle: 'Archivos se guardan en el volumen local del backend.',
    icon: HardDrive,
  },
  {
    value: 'S3_COMPATIBLE',
    label: 'S3 compatible',
    subtitle: 'Soporta proveedores tipo S3/S2 (AWS, MinIO, R2, etc).',
    icon: Database,
  },
];

const PAYMENT_GATEWAY_PROVIDER_OPTIONS: PaymentGatewayProviderOption[] = [
  {
    value: 'MERCADO_PAGO',
    label: 'Mercado Pago',
    subtitle: 'Checkout online con tarjetas y medios locales.',
  },
];

const BANK_ACCOUNT_TYPE_OPTIONS = [
  'Cuenta Corriente',
  'Cuenta Vista',
  'Cuenta RUT',
  'Cuenta de Ahorro',
  'Chequera Electronica',
];

const BANCO_ESTADO_ALLOWED_ACCOUNT_TYPES = ['Cuenta Corriente', 'Cuenta Vista', 'Cuenta RUT', 'Cuenta de Ahorro', 'Chequera Electronica'];

const CHILE_BANK_OPTIONS = [
  'Banco BICE',
  'Banco BTG Pactual Chile',
  'Banco Coopeuch',
  'Banco Consorcio',
  'Banco de Chile',
  'Banco de Credito e Inversiones (BCI)',
  'BancoEstado',
  'Banco Falabella',
  'Banco Internacional',
  'Banco Paris',
  'Banco Ripley',
  'Banco Santander Chile',
  'Banco Security',
  'Itau Chile',
  'Scotiabank Chile',
];

const SETTINGS_SUBMENU_TAB_IDS: SettingsSubmenuTab[] = ['store', 'payments', 'media', 'notifications'];

function normalizeBankName(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, '');
}

function isBancoEstado(bankName: string) {
  const normalized = normalizeBankName(bankName);
  return (
    normalized === 'bancoestado' ||
    normalized === 'bancodelestadodechile' ||
    normalized.includes('bancoestado') ||
    normalized.includes('estadodechile')
  );
}

function parseSettingsTab(rawValue: string | null): SettingsSubmenuTab {
  if (!rawValue) return 'store';
  const normalized = rawValue.toLowerCase();
  if (SETTINGS_SUBMENU_TAB_IDS.includes(normalized as SettingsSubmenuTab)) {
    return normalized as SettingsSubmenuTab;
  }
  return 'store';
}

function buildFormFromSettings(settings: SystemSettingsDto): FormState {
  return {
    whatsappNumber: settings.whatsappNumber ?? '',
    instagramUrl: settings.instagramUrl ?? '',
    facebookUrl: settings.facebookUrl ?? '',
    bankTransferAccountHolder: settings.bankTransferAccountHolder ?? '',
    bankTransferContactEmail: settings.bankTransferContactEmail ?? '',
    bankTransferAccountNumber: settings.bankTransferAccountNumber ?? '',
    bankTransferBankName: settings.bankTransferBankName ?? '',
    bankTransferAccountType: settings.bankTransferAccountType ?? '',
    paymentMethodBankTransferEnabled: settings.paymentMethodBankTransferEnabled ?? true,
    paymentMethodGatewayEnabled: settings.paymentMethodGatewayEnabled ?? true,
    paymentGatewayProviders: settings.paymentGatewayProviders?.length
      ? settings.paymentGatewayProviders
      : ['MERCADO_PAGO'],
    paymentGatewayMpApiBaseUrl: settings.paymentGatewayMpApiBaseUrl ?? 'https://api.mercadopago.com',
    paymentGatewayMpSuccessUrl: settings.paymentGatewayMpSuccessUrl ?? '',
    paymentGatewayMpPendingUrl: settings.paymentGatewayMpPendingUrl ?? '',
    paymentGatewayMpFailureUrl: settings.paymentGatewayMpFailureUrl ?? '',
    paymentGatewayMpNotificationUrl: settings.paymentGatewayMpNotificationUrl ?? '',
    paymentGatewayMpAccessToken: '',
    clearPaymentGatewayMpAccessToken: false,
    paymentGatewayMpWebhookToken: '',
    clearPaymentGatewayMpWebhookToken: false,
    mediaStorageProvider: settings.mediaStorageProvider ?? 'LOCAL',
    mediaS3Endpoint: settings.mediaS3Endpoint ?? '',
    mediaS3Region: settings.mediaS3Region ?? '',
    mediaS3Bucket: settings.mediaS3Bucket ?? '',
    mediaS3AccessKeyId: settings.mediaS3AccessKeyId ?? '',
    mediaS3SecretKey: '',
    clearMediaS3SecretKey: false,
    mediaS3PathStyleEnabled: settings.mediaS3PathStyleEnabled ?? false,
    mediaS3PublicBaseUrl: settings.mediaS3PublicBaseUrl ?? '',
    notificationProvider: settings.notificationProvider ?? 'LOG',
    n8nWebhookUrl: settings.n8nWebhookUrl ?? '',
    n8nTokenHeaderName: settings.n8nTokenHeaderName ?? 'X-PE-N8N-TOKEN',
    n8nApiKey: '',
    clearN8nApiKey: false,
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
    productAiInferDefaultBrand: settings.productAiInferDefaultBrand ?? 'Pilar Estilo',
    productAiInferDefaultCondition: settings.productAiInferDefaultCondition === 'NEW' ? 'NEW' : 'USED',
    productAiInferBasePrice: settings.productAiInferBasePrice != null ? String(settings.productAiInferBasePrice) : '24990',
    productAiInferListPriceMultiplier:
      settings.productAiInferListPriceMultiplier != null
        ? String(settings.productAiInferListPriceMultiplier)
        : '1.35',
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
  const [activeSettingsTab, setActiveSettingsTab] = useState<SettingsSubmenuTab>('store');
  const [tabSyncedFromUrl, setTabSyncedFromUrl] = useState(false);
  const [settings, setSettings] = useState<SystemSettingsDto | null>(null);
  const [migrating, setMigrating] = useState(false);
  const [migrateResult, setMigrateResult] = useState<{ migrated: number; failed: number } | null>(null);
  const [form, setForm] = useState<FormState>({
    whatsappNumber: '',
    instagramUrl: '',
    facebookUrl: '',
    bankTransferAccountHolder: '',
    bankTransferContactEmail: '',
    bankTransferAccountNumber: '',
    bankTransferBankName: '',
    bankTransferAccountType: '',
    paymentMethodBankTransferEnabled: true,
    paymentMethodGatewayEnabled: true,
    paymentGatewayProviders: ['MERCADO_PAGO'],
    paymentGatewayMpApiBaseUrl: 'https://api.mercadopago.com',
    paymentGatewayMpSuccessUrl: '',
    paymentGatewayMpPendingUrl: '',
    paymentGatewayMpFailureUrl: '',
    paymentGatewayMpNotificationUrl: '',
    paymentGatewayMpAccessToken: '',
    clearPaymentGatewayMpAccessToken: false,
    paymentGatewayMpWebhookToken: '',
    clearPaymentGatewayMpWebhookToken: false,
    mediaStorageProvider: 'LOCAL',
    mediaS3Endpoint: '',
    mediaS3Region: '',
    mediaS3Bucket: '',
    mediaS3AccessKeyId: '',
    mediaS3SecretKey: '',
    clearMediaS3SecretKey: false,
    mediaS3PathStyleEnabled: false,
    mediaS3PublicBaseUrl: '',
    notificationProvider: 'LOG',
    n8nWebhookUrl: '',
    n8nTokenHeaderName: 'X-PE-N8N-TOKEN',
    n8nApiKey: '',
    clearN8nApiKey: false,
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
    productAiInferDefaultBrand: 'Pilar Estilo',
    productAiInferDefaultCondition: 'USED',
    productAiInferBasePrice: '24990',
    productAiInferListPriceMultiplier: '1.35',
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

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    setActiveSettingsTab(parseSettingsTab(params.get('tab')));
    setTabSyncedFromUrl(true);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (!tabSyncedFromUrl) return;
    const url = new URL(window.location.href);
    const current = parseSettingsTab(url.searchParams.get('tab'));
    if (current === activeSettingsTab) return;
    url.searchParams.set('tab', activeSettingsTab);
    window.history.replaceState(window.history.state, '', `${url.pathname}?${url.searchParams.toString()}`);
  }, [activeSettingsTab, tabSyncedFromUrl]);

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setFeedback(null);
  }

  function toggleGatewayProvider(provider: PaymentGatewayProvider) {
    setForm((prev) => {
      const exists = prev.paymentGatewayProviders.includes(provider);
      if (exists) {
        if (prev.paymentGatewayProviders.length <= 1) {
          return prev;
        }
        return {
          ...prev,
          paymentGatewayProviders: prev.paymentGatewayProviders.filter((value) => value !== provider),
        };
      }
      return {
        ...prev,
        paymentGatewayProviders: [...prev.paymentGatewayProviders, provider],
      };
    });
    setFeedback(null);
  }

  const handleMigrateCategories = async () => {
    if (!effectiveToken) return;
    setMigrating(true);
    setMigrateResult(null);
    try {
      const result = await migrateCategoryImages(effectiveToken);
      setMigrateResult({ migrated: result.migrated, failed: result.failed });
    } catch {
      setMigrateResult({ migrated: 0, failed: -1 });
    } finally {
      setMigrating(false);
    }
  };

  const hasProviderRequiringSmtp = form.notificationProvider === 'EMAIL_SMTP';
  const hasProviderRequiringSendgrid = form.notificationProvider === 'EMAIL_SENDGRID';
  const hasProviderRequiringTwilio = form.notificationProvider === 'WHATSAPP_TWILIO';
  const hasProviderSimulated = form.notificationProvider === 'WHATSAPP_SIMULATED';
  const hasProviderN8n = form.notificationProvider === 'N8N_WEBHOOK';
  const hasS3CompatibleStorage = form.mediaStorageProvider === 'S3_COMPATIBLE';
  const hasMercadoPagoSelected = form.paymentGatewayProviders.includes('MERCADO_PAGO');
  const isBancoEstadoSelected = isBancoEstado(form.bankTransferBankName);
  const bankOptions = useMemo(() => {
    const current = form.bankTransferBankName.trim();
    if (current && !CHILE_BANK_OPTIONS.includes(current)) {
      return [current, ...CHILE_BANK_OPTIONS];
    }
    return CHILE_BANK_OPTIONS;
  }, [form.bankTransferBankName]);
  const bankAccountTypeOptions = useMemo(() => {
    if (isBancoEstadoSelected) {
      return BANCO_ESTADO_ALLOWED_ACCOUNT_TYPES;
    }
    return BANK_ACCOUNT_TYPE_OPTIONS.filter((accountType) => accountType !== 'Cuenta RUT');
  }, [isBancoEstadoSelected]);

  useEffect(() => {
    if (isBancoEstadoSelected) return;
    if (form.bankTransferAccountType !== 'Cuenta RUT') return;
    setForm((prev) => ({ ...prev, bankTransferAccountType: '' }));
    setFeedback(null);
  }, [isBancoEstadoSelected, form.bankTransferAccountType]);

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

    if (!form.mediaStorageProvider) {
      setFeedback({ tone: 'error', text: 'Debes seleccionar un proveedor de almacenamiento de imagenes.' });
      return;
    }

    if (!form.paymentMethodBankTransferEnabled && !form.paymentMethodGatewayEnabled) {
      setFeedback({ tone: 'error', text: 'Debes mantener al menos un medio de pago habilitado.' });
      return;
    }

    if (form.paymentMethodBankTransferEnabled) {
      if (!form.bankTransferAccountHolder.trim()) {
        setFeedback({ tone: 'error', text: 'Para transferencia debes indicar nombre del titular.' });
        return;
      }
      if (!form.bankTransferContactEmail.trim()) {
        setFeedback({ tone: 'error', text: 'Para transferencia debes indicar correo de contacto.' });
        return;
      }
      if (!form.bankTransferAccountNumber.trim()) {
        setFeedback({ tone: 'error', text: 'Para transferencia debes indicar numero de cuenta.' });
        return;
      }
      if (!form.bankTransferBankName.trim()) {
        setFeedback({ tone: 'error', text: 'Para transferencia debes indicar banco.' });
        return;
      }
      if (!form.bankTransferAccountType.trim()) {
        setFeedback({ tone: 'error', text: 'Para transferencia debes indicar tipo de cuenta.' });
        return;
      }
      if (!isBancoEstadoSelected && form.bankTransferAccountType.trim() === 'Cuenta RUT') {
        setFeedback({ tone: 'error', text: 'Cuenta RUT solo esta disponible para BancoEstado.' });
        return;
      }
    }

    if (form.paymentMethodGatewayEnabled && form.paymentGatewayProviders.length === 0) {
      setFeedback({ tone: 'error', text: 'Si habilitas pasarela de pago, debes seleccionar al menos un proveedor.' });
      return;
    }
    if (form.paymentMethodGatewayEnabled && hasMercadoPagoSelected && !form.paymentGatewayMpApiBaseUrl.trim()) {
      setFeedback({ tone: 'error', text: 'Para Mercado Pago debes indicar API base URL.' });
      return;
    }

    if (hasS3CompatibleStorage && !form.mediaS3Bucket.trim()) {
      setFeedback({ tone: 'error', text: 'Para almacenamiento S3-compatible debes indicar bucket.' });
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
    const aiInferBasePriceTrimmed = form.productAiInferBasePrice.trim();
    const aiInferBasePrice = aiInferBasePriceTrimmed ? Number(aiInferBasePriceTrimmed) : undefined;
    const aiInferListMultiplierTrimmed = form.productAiInferListPriceMultiplier.trim();
    const aiInferListMultiplier = aiInferListMultiplierTrimmed ? Number(aiInferListMultiplierTrimmed) : undefined;
    if (smtpPort !== undefined && (!Number.isFinite(smtpPort) || smtpPort <= 0 || smtpPort > 65535)) {
      setFeedback({ tone: 'error', text: 'El puerto SMTP debe estar entre 1 y 65535.' });
      return;
    }
    if (aiInferBasePrice !== undefined && (!Number.isFinite(aiInferBasePrice) || aiInferBasePrice < 1000)) {
      setFeedback({ tone: 'error', text: 'El precio base sugerido IA debe ser mayor o igual a 1000.' });
      return;
    }
    if (
      aiInferListMultiplier !== undefined &&
      (!Number.isFinite(aiInferListMultiplier) || aiInferListMultiplier < 1 || aiInferListMultiplier > 5)
    ) {
      setFeedback({ tone: 'error', text: 'El multiplicador de precio lista IA debe estar entre 1.00 y 5.00.' });
      return;
    }
    if (!form.productAiInferDefaultBrand.trim()) {
      setFeedback({ tone: 'error', text: 'La marca por defecto para inferencia IA no puede quedar vacia.' });
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

    if (hasProviderN8n) {
      const webhookUrl = form.n8nWebhookUrl.trim();
      if (webhookUrl && !/^https?:\/\//i.test(webhookUrl)) {
        setFeedback({ tone: 'error', text: 'La URL de webhook n8n debe iniciar con http:// o https://.' });
        return;
      }
      if (!form.n8nTokenHeaderName.trim()) {
        setFeedback({ tone: 'error', text: 'Para n8n debes indicar nombre de header para el token.' });
        return;
      }
    }

    const payload: UpdateSystemSettingsRequest = {
      whatsappNumber: whatsappTrimmed,
      instagramUrl: form.instagramUrl.trim(),
      facebookUrl: form.facebookUrl.trim(),
      bankTransferAccountHolder: form.bankTransferAccountHolder.trim(),
      bankTransferContactEmail: form.bankTransferContactEmail.trim(),
      bankTransferAccountNumber: form.bankTransferAccountNumber.trim(),
      bankTransferBankName: form.bankTransferBankName.trim(),
      bankTransferAccountType: form.bankTransferAccountType.trim(),
      paymentMethodBankTransferEnabled: form.paymentMethodBankTransferEnabled,
      paymentMethodGatewayEnabled: form.paymentMethodGatewayEnabled,
      paymentGatewayProviders: form.paymentGatewayProviders,
      paymentGatewayMpApiBaseUrl: form.paymentGatewayMpApiBaseUrl.trim(),
      paymentGatewayMpSuccessUrl: form.paymentGatewayMpSuccessUrl.trim(),
      paymentGatewayMpPendingUrl: form.paymentGatewayMpPendingUrl.trim(),
      paymentGatewayMpFailureUrl: form.paymentGatewayMpFailureUrl.trim(),
      paymentGatewayMpNotificationUrl: form.paymentGatewayMpNotificationUrl.trim(),
      paymentGatewayMpAccessToken: form.paymentGatewayMpAccessToken.trim(),
      clearPaymentGatewayMpAccessToken: form.clearPaymentGatewayMpAccessToken,
      paymentGatewayMpWebhookToken: form.paymentGatewayMpWebhookToken.trim(),
      clearPaymentGatewayMpWebhookToken: form.clearPaymentGatewayMpWebhookToken,
      mediaStorageProvider: form.mediaStorageProvider,
      mediaS3Endpoint: form.mediaS3Endpoint.trim(),
      mediaS3Region: form.mediaS3Region.trim(),
      mediaS3Bucket: form.mediaS3Bucket.trim(),
      mediaS3AccessKeyId: form.mediaS3AccessKeyId.trim(),
      mediaS3SecretKey: form.mediaS3SecretKey.trim(),
      clearMediaS3SecretKey: form.clearMediaS3SecretKey,
      mediaS3PathStyleEnabled: form.mediaS3PathStyleEnabled,
      mediaS3PublicBaseUrl: form.mediaS3PublicBaseUrl.trim(),
      notificationProvider: form.notificationProvider,
      n8nWebhookUrl: form.n8nWebhookUrl.trim(),
      n8nTokenHeaderName: form.n8nTokenHeaderName.trim(),
      n8nApiKey: form.n8nApiKey.trim(),
      clearN8nApiKey: form.clearN8nApiKey,
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
      productAiInferDefaultBrand: form.productAiInferDefaultBrand.trim(),
      productAiInferDefaultCondition: form.productAiInferDefaultCondition,
      productAiInferBasePrice: aiInferBasePrice,
      productAiInferListPriceMultiplier: aiInferListMultiplier,
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
  const selectedMediaStorageProvider = useMemo(
    () => MEDIA_STORAGE_OPTIONS.find((option) => option.value === form.mediaStorageProvider),
    [form.mediaStorageProvider]
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

      {activeSettingsTab === 'store' && (
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

          <div className="md:col-span-2 mt-2 rounded-sm border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">
              Defaults inferencia IA (Productos)
            </p>
            <p className="mt-1 font-sans text-[0.72rem] text-pe-charcoal/55">
              Se aplican al usar “Inferir texto con IA” en el formulario de productos.
            </p>
            <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Marca por defecto</span>
                <input
                  type="text"
                  value={form.productAiInferDefaultBrand}
                  onChange={(e) => updateField('productAiInferDefaultBrand', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="Pilar Estilo"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Condicion por defecto</span>
                <select
                  value={form.productAiInferDefaultCondition}
                  onChange={(e) => updateField('productAiInferDefaultCondition', e.target.value as 'NEW' | 'USED')}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                >
                  <option value="USED">Usado</option>
                  <option value="NEW">Nuevo</option>
                </select>
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Precio base sugerido (CLP)</span>
                <input
                  type="number"
                  min="1000"
                  step="1000"
                  value={form.productAiInferBasePrice}
                  onChange={(e) => updateField('productAiInferBasePrice', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="24990"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Multiplicador precio lista</span>
                <input
                  type="number"
                  min="1"
                  max="5"
                  step="0.01"
                  value={form.productAiInferListPriceMultiplier}
                  onChange={(e) => updateField('productAiInferListPriceMultiplier', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="1.35"
                />
              </label>
            </div>
          </div>
        </div>
      </section>
      )}

      {activeSettingsTab === 'payments' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Medios de pago</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
          Controla que opciones de pago estaran disponibles en checkout. Siempre debe quedar al menos una activa.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="inline-flex items-center gap-2 font-sans text-[0.8rem] text-pe-charcoal">
            <input
              type="checkbox"
              checked={form.paymentMethodBankTransferEnabled}
              onChange={(e) => {
                const next = e.target.checked;
                if (!next && !form.paymentMethodGatewayEnabled) return;
                updateField('paymentMethodBankTransferEnabled', next);
              }}
              className="h-4 w-4 accent-pe-rose"
            />
            Transferencia bancaria
          </label>

          <label className="inline-flex items-center gap-2 font-sans text-[0.8rem] text-pe-charcoal">
            <input
              type="checkbox"
              checked={form.paymentMethodGatewayEnabled}
              onChange={(e) => {
                const next = e.target.checked;
                if (!next && !form.paymentMethodBankTransferEnabled) return;
                updateField('paymentMethodGatewayEnabled', next);
              }}
              className="h-4 w-4 accent-pe-rose"
            />
            Pasarela de pago
          </label>
        </div>

        {form.paymentMethodBankTransferEnabled && (
          <div className="mt-4 rounded-sm border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">
              Datos de transferencia bancaria
            </p>
            <p className="mt-1 font-sans text-[0.72rem] text-pe-charcoal/55">
              Estos datos se muestran en checkout y se guardan como snapshot historico en cada pago por transferencia.
            </p>
            <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nombre titular</span>
                <input
                  type="text"
                  value={form.bankTransferAccountHolder}
                  onChange={(e) => updateField('bankTransferAccountHolder', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="Pilar Estilo Spa"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Correo contacto</span>
                <input
                  type="email"
                  value={form.bankTransferContactEmail}
                  onChange={(e) => updateField('bankTransferContactEmail', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="pagos@pilarestilo.com"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Numero de cuenta</span>
                <input
                  type="text"
                  value={form.bankTransferAccountNumber}
                  onChange={(e) => updateField('bankTransferAccountNumber', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                  placeholder="1234567890"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Banco</span>
                <select
                  value={form.bankTransferBankName}
                  onChange={(e) => updateField('bankTransferBankName', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                >
                  <option value="">Selecciona banco</option>
                  {bankOptions.map((bankName) => (
                    <option key={bankName} value={bankName}>
                      {bankName}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Tipo de cuenta</span>
                <select
                  value={form.bankTransferAccountType}
                  onChange={(e) => updateField('bankTransferAccountType', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                >
                  <option value="">Selecciona tipo de cuenta</option>
                  {bankAccountTypeOptions.map((accountType) => (
                    <option key={accountType} value={accountType}>
                      {accountType}
                    </option>
                  ))}
                </select>
                {!isBancoEstadoSelected && (
                  <span className="font-sans text-[0.7rem] text-pe-charcoal/55">
                    Cuenta RUT solo se habilita cuando seleccionas BancoEstado.
                  </span>
                )}
              </label>
            </div>
          </div>
        )}

        {form.paymentMethodGatewayEnabled && (
          <div className="mt-4 rounded-sm border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">
              Proveedores de pasarela activos
            </p>
            <div className="mt-2 grid grid-cols-1 md:grid-cols-2 gap-2">
              {PAYMENT_GATEWAY_PROVIDER_OPTIONS.map((option) => {
                const checked = form.paymentGatewayProviders.includes(option.value);
                const isLastSelected = checked && form.paymentGatewayProviders.length === 1;
                return (
                  <label key={option.value} className="inline-flex items-start gap-2 font-sans text-[0.78rem] text-pe-charcoal">
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={isLastSelected}
                      onChange={() => toggleGatewayProvider(option.value)}
                      className="mt-0.5 h-4 w-4 accent-pe-rose disabled:opacity-45"
                    />
                    <span>
                      <strong className="font-medium">{option.label}</strong>
                      <span className="block text-[0.72rem] text-pe-charcoal/60">{option.subtitle}</span>
                    </span>
                  </label>
                );
              })}
            </div>

            {hasMercadoPagoSelected && (
              <div className="mt-4 rounded-sm border border-pe-black/10 bg-pe-white px-3 py-3">
                <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">
                  Configuracion Mercado Pago
                </p>
                <p className="mt-1 font-sans text-[0.72rem] text-pe-charcoal/55">
                  Se usa para crear sesiones de checkout y validar webhooks. Si dejas campos vacios, se usa fallback desde .env.
                </p>

                <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">API base URL</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpApiBaseUrl}
                      onChange={(e) => updateField('paymentGatewayMpApiBaseUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="https://api.mercadopago.com"
                    />
                  </label>

                  <label className="flex flex-col gap-1 md:col-span-2">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nuevo Access Token</span>
                    <input
                      type="password"
                      value={form.paymentGatewayMpAccessToken}
                      onChange={(e) => updateField('paymentGatewayMpAccessToken', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="APP_USR-xxxxxxxxxxxxxxxx"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Back URL exito</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpSuccessUrl}
                      onChange={(e) => updateField('paymentGatewayMpSuccessUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Back URL pendiente</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpPendingUrl}
                      onChange={(e) => updateField('paymentGatewayMpPendingUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Back URL fallo</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpFailureUrl}
                      onChange={(e) => updateField('paymentGatewayMpFailureUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Notification URL</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpNotificationUrl}
                      onChange={(e) => updateField('paymentGatewayMpNotificationUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="https://tudominio.com/api/payments/webhooks/gateway/mercadopago"
                    />
                  </label>

                  <label className="flex flex-col gap-1 md:col-span-2">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nuevo Webhook Token (opcional)</span>
                    <input
                      type="password"
                      value={form.paymentGatewayMpWebhookToken}
                      onChange={(e) => updateField('paymentGatewayMpWebhookToken', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                      placeholder="token-seguro-opcional"
                    />
                  </label>
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-4">
                  <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
                    <input
                      type="checkbox"
                      checked={form.clearPaymentGatewayMpAccessToken}
                      onChange={(e) => updateField('clearPaymentGatewayMpAccessToken', e.target.checked)}
                      className="h-4 w-4 accent-pe-rose"
                    />
                    Limpiar access token guardado
                  </label>

                  <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
                    <input
                      type="checkbox"
                      checked={form.clearPaymentGatewayMpWebhookToken}
                      onChange={(e) => updateField('clearPaymentGatewayMpWebhookToken', e.target.checked)}
                      className="h-4 w-4 accent-pe-rose"
                    />
                    Limpiar webhook token guardado
                  </label>
                </div>

                <div className="mt-2 grid grid-cols-1 md:grid-cols-2 gap-2">
                  <SecurityHint
                    configured={Boolean(settings?.paymentGatewayMpAccessTokenConfigured)}
                    clearFlag={form.clearPaymentGatewayMpAccessToken}
                    newValue={form.paymentGatewayMpAccessToken}
                    emptyText="Sin access token Mercado Pago configurado."
                    keepText="Hay un access token Mercado Pago guardado (no visible)."
                    replaceText="Se reemplazara el access token Mercado Pago actual."
                    clearText="Se eliminara el access token Mercado Pago al guardar."
                  />
                  <SecurityHint
                    configured={Boolean(settings?.paymentGatewayMpWebhookTokenConfigured)}
                    clearFlag={form.clearPaymentGatewayMpWebhookToken}
                    newValue={form.paymentGatewayMpWebhookToken}
                    emptyText="Sin webhook token Mercado Pago configurado."
                    keepText="Hay un webhook token Mercado Pago guardado (no visible)."
                    replaceText="Se reemplazara el webhook token Mercado Pago actual."
                    clearText="Se eliminara el webhook token Mercado Pago al guardar."
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </section>
      )}

      {activeSettingsTab === 'media' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Almacenamiento de imagenes</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
          Elige donde guardar imagenes de productos y comprobantes.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {MEDIA_STORAGE_OPTIONS.map((option) => {
            const Icon = option.icon;
            const isActive = form.mediaStorageProvider === option.value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => updateField('mediaStorageProvider', option.value)}
                className={[
                  'text-left border px-3 py-3 transition',
                  isActive
                    ? 'border-pe-rose bg-pe-rose/10'
                    : 'border-pe-black/10 hover:border-pe-rose/45 hover:bg-pe-rose/10',
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
            Activo ahora: <strong>{selectedMediaStorageProvider?.label ?? form.mediaStorageProvider}</strong>
          </span>
        </div>

        <div className="pt-4 border-t border-pe-black/8">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45 mb-2">
            Migración de imágenes
          </p>
          <p className="font-sans text-[0.72rem] text-pe-charcoal/60 mb-3">
            Descarga las imágenes de categorías desde URLs externas al almacenamiento configurado.
            Solo procesa imágenes que aún no estén almacenadas localmente.
          </p>
          <button
            type="button"
            onClick={handleMigrateCategories}
            disabled={migrating}
            className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-50"
          >
            {migrating ? <Loader2 size={13} className="animate-spin" /> : null}
            {migrating ? 'Migrando...' : 'Migrar imágenes de categorías'}
          </button>
          {migrateResult && (
            <p className={`font-sans text-[0.72rem] mt-2 ${migrateResult.failed === -1 ? 'text-red-500' : 'text-pe-charcoal/60'}`}>
              {migrateResult.failed === -1
                ? 'Error al ejecutar la migración.'
                : `Migradas: ${migrateResult.migrated} · Fallidas: ${migrateResult.failed}`}
            </p>
          )}
        </div>
      </section>
      )}

      {activeSettingsTab === 'media' && hasS3CompatibleStorage && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">Configuracion S3 compatible</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            Compatible con proveedores tipo S3/S2 (AWS S3, MinIO, Cloudflare R2, Wasabi, etc).
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Endpoint</span>
              <input
                type="url"
                value={form.mediaS3Endpoint}
                onChange={(e) => updateField('mediaS3Endpoint', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="https://s3.amazonaws.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Region</span>
              <input
                type="text"
                value={form.mediaS3Region}
                onChange={(e) => updateField('mediaS3Region', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="us-east-1"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Bucket</span>
              <input
                type="text"
                value={form.mediaS3Bucket}
                onChange={(e) => updateField('mediaS3Bucket', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="pilarestilo-media"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Access Key ID</span>
              <input
                type="text"
                value={form.mediaS3AccessKeyId}
                onChange={(e) => updateField('mediaS3AccessKeyId', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="AKIA..."
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nuevo Secret Access Key</span>
              <input
                type="password"
                value={form.mediaS3SecretKey}
                onChange={(e) => updateField('mediaS3SecretKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Base URL publica (opcional)</span>
              <input
                type="url"
                value={form.mediaS3PublicBaseUrl}
                onChange={(e) => updateField('mediaS3PublicBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="https://cdn.tu-dominio.com"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.mediaS3PathStyleEnabled}
                onChange={(e) => updateField('mediaS3PathStyleEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Path style habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.clearMediaS3SecretKey}
                onChange={(e) => updateField('clearMediaS3SecretKey', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Limpiar Secret Access Key guardado
            </label>
          </div>

          <SecurityHint
            configured={Boolean(settings?.mediaS3SecretKeyConfigured)}
            clearFlag={form.clearMediaS3SecretKey}
            newValue={form.mediaS3SecretKey}
            emptyText="Sin Secret Access Key configurado."
            keepText="Hay un Secret Access Key guardado (no visible)."
            replaceText="Se reemplazara el Secret Access Key actual."
            clearText="Se eliminara el Secret Access Key al guardar."
          />
        </section>
      )}

      {activeSettingsTab === 'notifications' && (
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
                    : 'border-pe-black/10 hover:border-pe-rose/45 hover:bg-pe-rose/10',
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
      )}

      {activeSettingsTab === 'notifications' && hasProviderSimulated && (
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

      {activeSettingsTab === 'notifications' && hasProviderRequiringTwilio && (
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

      {activeSettingsTab === 'notifications' && hasProviderRequiringSendgrid && (
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

      {activeSettingsTab === 'notifications' && hasProviderRequiringSmtp && (
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

      {activeSettingsTab === 'notifications' && hasProviderN8n && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">N8N Webhook</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/55">
            Configura webhook y token para delegar notificaciones a flujos n8n.
            Si dejas campos vacios, el backend usa fallback desde variables de entorno.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Webhook URL</span>
              <input
                type="url"
                value={form.n8nWebhookUrl}
                onChange={(e) => updateField('n8nWebhookUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="https://n8n.tudominio.com/webhook/pilar-notifications"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Header token</span>
              <input
                type="text"
                value={form.n8nTokenHeaderName}
                onChange={(e) => updateField('n8nTokenHeaderName', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="X-PE-N8N-TOKEN"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-charcoal/55">Nuevo API key/token</span>
              <input
                type="password"
                value={form.n8nApiKey}
                onChange={(e) => updateField('n8nApiKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-none"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-charcoal/70">
              <input
                type="checkbox"
                checked={form.clearN8nApiKey}
                onChange={(e) => updateField('clearN8nApiKey', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Limpiar API key n8n guardada
            </label>
          </div>

          <SecurityHint
            configured={Boolean(settings?.n8nApiKeyConfigured)}
            clearFlag={form.clearN8nApiKey}
            newValue={form.n8nApiKey}
            emptyText="Sin API key n8n configurada."
            keepText="Hay una API key n8n guardada (no visible)."
            replaceText="Se reemplazara la API key n8n actual."
            clearText="Se eliminara la API key n8n al guardar."
          />
          <div className="mt-3 rounded-sm border border-pe-black/10 bg-pe-offwhite px-3 py-2">
            <span className="font-sans text-[0.72rem] text-pe-charcoal/70">
              Tip: cada cliente puede elegir su canal preferido (WhatsApp/Correo/Ambos) en Mi Cuenta y n8n puede enrutar en base a ese dato.
              Si no completas estos campos, se usan fallback desde <span className="font-mono text-[0.7rem]">NOTIFICATION_N8N_*</span>.
            </span>
          </div>
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
