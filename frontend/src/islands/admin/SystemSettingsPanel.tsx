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
  searchLocationCommunes,
  getHeroModels,
  getSystemSettings,
  migrateCategoryImages,
  optimizeAllMedia,
  resizeProductsCategoriesTo15cm,
  type CourierConfig,
  type HeroModelSlot,
  type HeroModelsDto,
  type MediaStorageProvider,
  type LocationCommuneDto,
  type OptimizeAllResult,
  type PaymentGatewayProvider,
  type ResizeProductsCategoriesResult,
  type ShippingPaymentMode,
  type ShippingZoneConfig,
  uploadHeroModel,
  updateSystemSettings,
  type NotificationProvider,
  type SystemSettingsDto,
  type UpdateSystemSettingsRequest,
} from '../../lib/api';
import { readAuthTokenCookie, useAuthStore } from '../../lib/authStore';
import { useCan } from '../../lib/permissions';
import ImageDropzone from './ImageDropzone';

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
  bankTransferAutoCancelEnabled: boolean;
  bankTransferAutoCancelTimeoutMinutes: number;
  bankTransferAutoCancelCron: string;
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
  notificationProviders: NotificationProvider[];
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
  shippingZones: ShippingZoneConfig[];
  shippingCouriers: CourierConfig[];
  shippingPaymentMode: ShippingPaymentMode;
  taxPayerRut: string;
  taxBusinessName: string;
  taxBusinessActivity: string;
  taxActecoCode: string;
  taxAddress: string;
  taxCommune: string;
  taxCity: string;
  taxVatRate: string;
  taxDocumentRequiredBeforeDispatch: boolean;
  taxDocumentProvider: 'MANUAL' | 'TUU' | 'OPENFACTURA';
  /** Configured from /admin/descuentos, not this page — carried through untouched on save. */
  welcomeDiscountEnabled: boolean;
  welcomeDiscountType: 'PERCENTAGE' | 'FIXED';
  welcomeDiscountValue: number;
  welcomeDiscountMinOrderAmount: number;
  welcomeDiscountRequiresMarketing: boolean;
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

type SettingsSubmenuTab = 'store' | 'payments' | 'media' | 'notifications' | 'shipping' | 'tributarios';

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

const SETTINGS_SUBMENU_TAB_IDS: SettingsSubmenuTab[] = ['store', 'payments', 'media', 'notifications', 'shipping', 'tributarios'];

const DEFAULT_SHIPPING_ZONES: ShippingZoneConfig[] = [
  { code: 'LOCAL', titleEs: 'Zona local', titleEn: 'Local zone',
    etaEs: '24-48 hs', etaEn: '24-48h',
    comunas: ['Los Andes', 'San Felipe', 'Calle Larga', 'Rinconada'],
    active: true, sortOrder: 1 },
  { code: 'REGIONAL', titleEs: 'V Region y RM', titleEn: 'Valparaiso Region and Metropolitan Region',
    etaEs: '2-4 dias habiles', etaEn: '2-4 business days',
    comunas: [], active: true, sortOrder: 2 },
  { code: 'NACIONAL', titleEs: 'Otras regiones', titleEn: 'Other Chilean regions',
    etaEs: '3-7 dias habiles', etaEn: '3-7 business days',
    comunas: [], active: true, sortOrder: 3 },
];

const DEFAULT_SHIPPING_COURIERS: CourierConfig[] = [
  { id: 'starken', name: 'Starken', logoUrl: null, active: true },
  { id: 'chilexpress', name: 'ChilExpress', logoUrl: null, active: true },
];

function parseShippingZones(raw: string | null | undefined): ShippingZoneConfig[] {
  if (!raw) return DEFAULT_SHIPPING_ZONES;
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return DEFAULT_SHIPPING_ZONES;
    return parsed.map((z: Partial<ShippingZoneConfig>) => ({
      code: (z.code as ShippingZoneConfig['code']) ?? 'LOCAL',
      titleEs: z.titleEs ?? '',
      titleEn: z.titleEn ?? '',
      etaEs: z.etaEs ?? '',
      etaEn: z.etaEn ?? '',
      comunas: Array.isArray(z.comunas) ? z.comunas.filter((c): c is string => typeof c === 'string') : [],
      active: z.active !== false,
      sortOrder: typeof z.sortOrder === 'number' ? z.sortOrder : 0,
    }));
  } catch {
    return DEFAULT_SHIPPING_ZONES;
  }
}

function parseShippingCouriers(raw: string | null | undefined): CourierConfig[] {
  if (!raw) return DEFAULT_SHIPPING_COURIERS;
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return DEFAULT_SHIPPING_COURIERS;
    return parsed.map((c: Partial<CourierConfig>) => ({
      id: c.id ?? '',
      name: c.name ?? '',
      logoUrl: c.logoUrl ?? null,
      active: c.active !== false,
    })).filter((c) => c.id && c.name);
  } catch {
    return DEFAULT_SHIPPING_COURIERS;
  }
}

function slugifyCourierId(name: string): string {
  return name.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

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
    bankTransferAutoCancelEnabled: settings.bankTransferAutoCancelEnabled ?? true,
    bankTransferAutoCancelTimeoutMinutes: settings.bankTransferAutoCancelTimeoutMinutes ?? 30,
    bankTransferAutoCancelCron: settings.bankTransferAutoCancelCron ?? '0 */15 * * * *',
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
    notificationProviders: settings.notificationProviders?.length
      ? settings.notificationProviders
      : ['LOG'],
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
    shippingZones: parseShippingZones(settings.shippingZonesJson),
    shippingCouriers: parseShippingCouriers(settings.shippingCouriersJson),
    shippingPaymentMode: settings.shippingPaymentMode ?? 'POR_PAGAR',
    taxPayerRut: settings.taxPayerRut ?? '',
    taxBusinessName: settings.taxBusinessName ?? '',
    taxBusinessActivity: settings.taxBusinessActivity ?? '',
    taxActecoCode: settings.taxActecoCode ?? '',
    taxAddress: settings.taxAddress ?? '',
    taxCommune: settings.taxCommune ?? '',
    taxCity: settings.taxCity ?? '',
    taxVatRate: settings.taxVatRate != null ? String(settings.taxVatRate) : '19',
    taxDocumentRequiredBeforeDispatch: settings.taxDocumentRequiredBeforeDispatch ?? true,
    taxDocumentProvider: settings.taxDocumentProvider ?? 'MANUAL',
    welcomeDiscountEnabled: settings.welcomeDiscountEnabled ?? false,
    welcomeDiscountType: settings.welcomeDiscountType ?? 'PERCENTAGE',
    welcomeDiscountValue: settings.welcomeDiscountValue ?? 10,
    welcomeDiscountMinOrderAmount: settings.welcomeDiscountMinOrderAmount ?? 0,
    welcomeDiscountRequiresMarketing: settings.welcomeDiscountRequiresMarketing ?? true,
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

function formatEpochTimestamp(value?: number) {
  if (!value || value <= 0) return 'Sin registro';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Sin registro';
  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function heroSlotLabel(slot: HeroModelSlot) {
  return slot === 'left' ? 'Modelo izquierda' : 'Modelo derecha';
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
  readonly configured: boolean;
  readonly clearFlag: boolean;
  readonly newValue: string;
  readonly emptyText: string;
  readonly replaceText: string;
  readonly clearText: string;
  readonly keepText: string;
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
    <div className="mt-3 inline-flex items-center gap-2 rounded-xs border border-pe-black/10 bg-pe-offwhite px-2.5 py-2">
      {configured && !clearFlag ? (
        <ShieldCheck size={14} className="text-pe-positive" />
      ) : (
        <ShieldX size={14} className="text-amber-700" />
      )}
      <span className="font-sans text-[0.72rem] text-pe-muted">{hint}</span>
    </div>
  );
}

export default function SystemSettingsPanel() {
  const { token, user } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const canReadSettings = useCan('settings.read', 'configuracion');
  const canUpdateSettings = useCan('settings.update', 'configuracion');
  const isLegacyAdmin = user?.role === 'ADMIN';

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [activeSettingsTab, setActiveSettingsTab] = useState<SettingsSubmenuTab>('store');
  const [tabSyncedFromUrl, setTabSyncedFromUrl] = useState(false);
  const [settings, setSettings] = useState<SystemSettingsDto | null>(null);
  const [migrating, setMigrating] = useState(false);
  const [migrateResult, setMigrateResult] = useState<{ migrated: number; failed: number } | null>(null);
  const [optimizing, setOptimizing] = useState(false);
  const [optimizeResult, setOptimizeResult] = useState<OptimizeAllResult | null>(null);
  const [optimizeError, setOptimizeError] = useState<string | null>(null);
  const [resizingTo15cm, setResizingTo15cm] = useState(false);
  const [resizeTo15cmResult, setResizeTo15cmResult] = useState<ResizeProductsCategoriesResult | null>(null);
  const [resizeTo15cmError, setResizeTo15cmError] = useState<string | null>(null);
  const [heroModels, setHeroModels] = useState<HeroModelsDto | null>(null);
  const [heroLoading, setHeroLoading] = useState(false);
  const [heroUploadSlot, setHeroUploadSlot] = useState<HeroModelSlot | null>(null);
  const [shippingCommuneSearch, setShippingCommuneSearch] = useState<Record<string, string>>({});
  const [shippingCommuneResults, setShippingCommuneResults] = useState<Record<string, LocationCommuneDto[]>>({});
  const [shippingCommuneLoading, setShippingCommuneLoading] = useState<Record<string, boolean>>({});
  const [form, setForm] = useState<FormState>({
    whatsappNumber: '',
    instagramUrl: '',
    facebookUrl: '',
    bankTransferAccountHolder: '',
    bankTransferContactEmail: '',
    bankTransferAccountNumber: '',
    bankTransferBankName: '',
    bankTransferAccountType: '',
    bankTransferAutoCancelEnabled: true,
    bankTransferAutoCancelTimeoutMinutes: 30,
    bankTransferAutoCancelCron: '0 */15 * * * *',
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
    notificationProviders: ['LOG'],
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
    shippingZones: DEFAULT_SHIPPING_ZONES,
    shippingCouriers: DEFAULT_SHIPPING_COURIERS,
    shippingPaymentMode: 'POR_PAGAR',
    taxPayerRut: '',
    taxBusinessName: '',
    taxBusinessActivity: '',
    taxActecoCode: '',
    taxAddress: '',
    taxCommune: '',
    taxCity: '',
    taxVatRate: '19',
    taxDocumentRequiredBeforeDispatch: true,
    taxDocumentProvider: 'MANUAL',
    welcomeDiscountEnabled: false,
    welcomeDiscountType: 'PERCENTAGE',
    welcomeDiscountValue: 10,
    welcomeDiscountMinOrderAmount: 0,
    welcomeDiscountRequiresMarketing: true,
  });

  async function loadSettings() {
    if (!canReadSettings) {
      setLoading(false);
      return;
    }
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
      void loadHeroModels(effectiveToken);
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

  async function loadHeroModels(tokenToUse?: string) {
    if (!isLegacyAdmin) return;
    const adminToken = tokenToUse ?? effectiveToken;
    if (!adminToken) return;
    setHeroLoading(true);
    try {
      const models = await getHeroModels(adminToken);
      setHeroModels(models);
    } catch {
      setHeroModels(null);
    } finally {
      setHeroLoading(false);
    }
  }

  useEffect(() => {
    void loadSettings();
  }, [effectiveToken, canReadSettings]);

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

  /**
   * Channels are cumulative: a shop that sends WhatsApp still owes its customers the written
   * confirmation by email. The last one cannot be turned off — a shop with no channel notifies
   * nobody and says nothing about why.
   */
  function toggleNotificationProvider(provider: NotificationProvider) {
    setForm((prev) => {
      if (prev.notificationProviders.includes(provider)) {
        if (prev.notificationProviders.length <= 1) return prev;
        return {
          ...prev,
          notificationProviders: prev.notificationProviders.filter((value) => value !== provider),
        };
      }
      return { ...prev, notificationProviders: [...prev.notificationProviders, provider] };
    });
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

  function addCommuneToZone(idx: number, comunaName: string) {
    const value = comunaName.trim();
    if (!value) return;
    const next = [...form.shippingZones];
    const zone = next[idx];
    if (!zone) return;
    const exists = zone.comunas.some((entry) => entry.toLowerCase() === value.toLowerCase());
    if (exists) return;
    next[idx] = {
      ...zone,
      comunas: [...zone.comunas, value].sort((a, b) => a.localeCompare(b, 'es', { sensitivity: 'base' })),
    };
    updateField('shippingZones', next);
  }

  async function handleShippingCommuneSearch(zoneCode: string, query: string) {
    setShippingCommuneSearch((prev) => ({ ...prev, [zoneCode]: query }));
    const normalized = query.trim();
    if (normalized.length < 2) {
      setShippingCommuneResults((prev) => ({ ...prev, [zoneCode]: [] }));
      setShippingCommuneLoading((prev) => ({ ...prev, [zoneCode]: false }));
      return;
    }
    setShippingCommuneLoading((prev) => ({ ...prev, [zoneCode]: true }));
    try {
      const rows = await searchLocationCommunes({ q: normalized, limit: 10 });
      setShippingCommuneResults((prev) => ({ ...prev, [zoneCode]: rows }));
    } catch {
      setShippingCommuneResults((prev) => ({ ...prev, [zoneCode]: [] }));
    } finally {
      setShippingCommuneLoading((prev) => ({ ...prev, [zoneCode]: false }));
    }
  }

  const handleMigrateCategories = async () => {
    if (!isLegacyAdmin) return;
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

  const handleOptimizeAll = async () => {
    if (!isLegacyAdmin) return;
    if (!effectiveToken || optimizing) return;
    if (!confirm('Esto reescribira todas las imagenes existentes (productos + categorias + resto). El proceso puede tardar varios minutos. Continuar?')) {
      return;
    }
    setOptimizing(true);
    setOptimizeResult(null);
    setOptimizeError(null);
    try {
      const result = await optimizeAllMedia(effectiveToken);
      setOptimizeResult(result);
    } catch (err) {
      setOptimizeError(err instanceof Error ? err.message : 'Error al optimizar imagenes');
    } finally {
      setOptimizing(false);
    }
  };

  const handleResizeTo15cm = async () => {
    if (!isLegacyAdmin) return;
    if (!effectiveToken || resizingTo15cm) return;
    if (!confirm('Esto redimensionara imagenes de productos y categorias a 15 cm maximo por lado mayor, manteniendo proporcion. Continuar?')) {
      return;
    }
    setResizingTo15cm(true);
    setResizeTo15cmResult(null);
    setResizeTo15cmError(null);
    try {
      const result = await resizeProductsCategoriesTo15cm(effectiveToken);
      setResizeTo15cmResult(result);
    } catch (err) {
      setResizeTo15cmError(err instanceof Error ? err.message : 'Error al redimensionar imagenes');
    } finally {
      setResizingTo15cm(false);
    }
  };

  const handleHeroUpload = async (slot: HeroModelSlot, file: File) => {
    if (!isLegacyAdmin) {
      throw new Error('Solo administracion legacy puede actualizar modelos del hero.');
    }
    if (!effectiveToken) {
      throw new Error('No hay sesion admin activa.');
    }
    setHeroUploadSlot(slot);
    setFeedback(null);
    try {
      const saved = await uploadHeroModel(slot, file, effectiveToken);
      setHeroModels((prev) => {
        const next = prev ?? {
          left: { slot: 'left', url: '/api/media/hero-models/hero-left.png', updatedAt: 0 },
          right: { slot: 'right', url: '/api/media/hero-models/hero-right.png', updatedAt: 0 },
        };
        return slot === 'left' ? { ...next, left: saved } : { ...next, right: saved };
      });
      setFeedback({ tone: 'success', text: `Imagen de ${heroSlotLabel(slot).toLowerCase()} actualizada.` });
      return saved;
    } catch (error) {
      setFeedback({
        tone: 'error',
        text: error instanceof Error ? error.message : 'No se pudo subir la imagen del hero.',
      });
      throw error instanceof Error ? error : new Error('No se pudo subir la imagen del hero.');
    } finally {
      setHeroUploadSlot(null);
    }
  };

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const hasProviderRequiringSmtp = form.notificationProviders.includes('EMAIL_SMTP');
  const hasProviderRequiringSendgrid = form.notificationProviders.includes('EMAIL_SENDGRID');
  const hasProviderRequiringTwilio = form.notificationProviders.includes('WHATSAPP_TWILIO');
  const hasProviderSimulated = form.notificationProviders.includes('WHATSAPP_SIMULATED');
  const hasProviderN8n = form.notificationProviders.includes('N8N_WEBHOOK');
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
    if (!canUpdateSettings) {
      setFeedback({ tone: 'error', text: 'Tu sesion solo tiene acceso de lectura para esta configuracion.' });
      return;
    }
    if (!effectiveToken || saving) return;

    const whatsappTrimmed = form.whatsappNumber.trim();
    if (!whatsappTrimmed) {
      setFeedback({ tone: 'error', text: 'El numero de WhatsApp de la tienda es obligatorio.' });
      return;
    }

    if (form.notificationProviders.length === 0) {
      setFeedback({ tone: 'error', text: 'Deja al menos un canal de notificaciones activo.' });
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
      notificationProviders: form.notificationProviders,
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
      shippingZonesJson: JSON.stringify(form.shippingZones),
      shippingCouriersJson: JSON.stringify(form.shippingCouriers),
      shippingPaymentMode: form.shippingPaymentMode,
      taxPayerRut: form.taxPayerRut.trim() || null,
      taxBusinessName: form.taxBusinessName.trim() || null,
      taxBusinessActivity: form.taxBusinessActivity.trim() || null,
      taxActecoCode: form.taxActecoCode.trim() || null,
      taxAddress: form.taxAddress.trim() || null,
      taxCommune: form.taxCommune.trim() || null,
      taxCity: form.taxCity.trim() || null,
      taxVatRate: form.taxVatRate.trim() ? Number(form.taxVatRate) : null,
      taxDocumentRequiredBeforeDispatch: form.taxDocumentRequiredBeforeDispatch,
      taxDocumentProvider: form.taxDocumentProvider,
      bankTransferAutoCancelEnabled: form.bankTransferAutoCancelEnabled,
      bankTransferAutoCancelTimeoutMinutes: form.bankTransferAutoCancelTimeoutMinutes,
      bankTransferAutoCancelCron: form.bankTransferAutoCancelCron,
      // Configured on /admin/descuentos; carried through untouched so this save doesn't reset it.
      welcomeDiscountEnabled: form.welcomeDiscountEnabled,
      welcomeDiscountType: form.welcomeDiscountType,
      welcomeDiscountValue: form.welcomeDiscountValue,
      welcomeDiscountMinOrderAmount: form.welcomeDiscountMinOrderAmount,
      welcomeDiscountRequiresMarketing: form.welcomeDiscountRequiresMarketing,
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

  const selectedMediaStorageProvider = useMemo(
    () => MEDIA_STORAGE_OPTIONS.find((option) => option.value === form.mediaStorageProvider),
    [form.mediaStorageProvider]
  );

  if (loading) {
    return (
      <div className="border border-pe-black/10 bg-pe-white p-6 flex items-center gap-2 text-pe-muted">
        <Loader2 size={15} className="animate-spin" />
        <span className="font-sans text-sm">Cargando configuracion...</span>
      </div>
    );
  }

  if (!canReadSettings) {
    return (
      <div className="border border-pe-black/10 bg-pe-white p-6">
        <p className="font-sans text-[0.66rem] uppercase tracking-[0.18em] text-pe-muted">Configuracion</p>
        <h2 className="mt-2 font-display text-2xl font-light text-pe-black">Acceso restringido</h2>
        <p className="mt-2 max-w-xl font-sans text-[0.8rem] leading-relaxed text-pe-muted">
          Esta cuenta no tiene permiso para consultar la configuracion operativa de la tienda.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="font-sans text-[0.74rem] text-pe-muted">
          Ultima actualizacion: {formatTimestamp(settings?.updatedAt)}{settings?.updatedBy ? ` por ${settings.updatedBy}` : ''}
        </div>
        <button
          type="button"
          onClick={() => void loadSettings()}
          disabled={saving}
          className="inline-flex items-center gap-1 border border-pe-black/15 px-3 py-2 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-muted hover:bg-pe-cream disabled:opacity-50"
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
              ? 'border-green-200 bg-green-50 text-green-800'
              : 'border-red-200 bg-red-50 text-red-700',
          ].join(' ')}
        >
          {feedback.text}
        </div>
      )}

      {!canUpdateSettings && (
        <div className="border border-pe-black/10 bg-pe-offwhite px-4 py-3">
          <p className="font-sans text-[0.72rem] text-pe-muted">
            Modo consulta. Puedes revisar la configuracion vigente, pero guardar cambios requiere <span className="font-mono text-[0.7rem]">settings.update</span>.
          </p>
        </div>
      )}

      <fieldset disabled={!canUpdateSettings} className="flex flex-col gap-5 disabled:opacity-80">

      {activeSettingsTab === 'store' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Canales tienda</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
          Estos valores alimentan el boton flotante de WhatsApp y los enlaces sociales del storefront.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 md:col-span-2">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Numero WhatsApp</span>
            <input
              type="text"
              value={form.whatsappNumber}
              onChange={(e) => updateField('whatsappNumber', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="+56912345678"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">URL Instagram</span>
            <input
              type="url"
              value={form.instagramUrl}
              onChange={(e) => updateField('instagramUrl', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="https://instagram.com/tu_cuenta"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">URL Facebook</span>
            <input
              type="url"
              value={form.facebookUrl}
              onChange={(e) => updateField('facebookUrl', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="https://facebook.com/tu_pagina"
            />
          </label>

          <div className="md:col-span-2 mt-2 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
              Defaults inferencia IA (Productos)
            </p>
            <p className="mt-1 font-sans text-[0.72rem] text-pe-muted">
              Se aplican al usar “Inferir texto con IA” en el formulario de productos.
            </p>
            <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Marca por defecto</span>
                <input
                  type="text"
                  value={form.productAiInferDefaultBrand}
                  onChange={(e) => updateField('productAiInferDefaultBrand', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  placeholder="Pilar Estilo"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Condicion por defecto</span>
                <select
                  value={form.productAiInferDefaultCondition}
                  onChange={(e) => updateField('productAiInferDefaultCondition', e.target.value as 'NEW' | 'USED')}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                >
                  <option value="USED">Usado</option>
                  <option value="NEW">Nuevo</option>
                </select>
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Precio base sugerido (CLP)</span>
                <input
                  type="number"
                  min="1000"
                  step="1000"
                  value={form.productAiInferBasePrice}
                  onChange={(e) => updateField('productAiInferBasePrice', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  placeholder="24990"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Multiplicador precio lista</span>
                <input
                  type="number"
                  min="1"
                  max="5"
                  step="0.01"
                  value={form.productAiInferListPriceMultiplier}
                  onChange={(e) => updateField('productAiInferListPriceMultiplier', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
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
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
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
          <div className="mt-4 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
              Datos de transferencia bancaria
            </p>
            <p className="mt-1 font-sans text-[0.72rem] text-pe-muted">
              Estos datos se muestran en checkout y se guardan como snapshot historico en cada pago por transferencia.
            </p>
            <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nombre titular</span>
                <input
                  type="text"
                  value={form.bankTransferAccountHolder}
                  onChange={(e) => updateField('bankTransferAccountHolder', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  placeholder="Pilar Estilo Spa"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Correo contacto</span>
                <input
                  type="email"
                  value={form.bankTransferContactEmail}
                  onChange={(e) => updateField('bankTransferContactEmail', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  placeholder="pagos@pilarestilo.com"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Numero de cuenta</span>
                <input
                  type="text"
                  value={form.bankTransferAccountNumber}
                  onChange={(e) => updateField('bankTransferAccountNumber', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  placeholder="1234567890"
                />
              </label>

              <label className="flex flex-col gap-1">
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Banco</span>
                <select
                  value={form.bankTransferBankName}
                  onChange={(e) => updateField('bankTransferBankName', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
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
                <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Tipo de cuenta</span>
                <select
                  value={form.bankTransferAccountType}
                  onChange={(e) => updateField('bankTransferAccountType', e.target.value)}
                  className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                >
                  <option value="">Selecciona tipo de cuenta</option>
                  {bankAccountTypeOptions.map((accountType) => (
                    <option key={accountType} value={accountType}>
                      {accountType}
                    </option>
                  ))}
                </select>
                {!isBancoEstadoSelected && (
                  <span className="font-sans text-[0.7rem] text-pe-muted">
                    Cuenta RUT solo se habilita cuando seleccionas BancoEstado.
                  </span>
                )}
              </label>
            </div>

            <div className="mt-4 border-t border-pe-black/10 pt-4">
              <p className="mb-3 font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
                Cancelación automática por falta de comprobante
              </p>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={form.bankTransferAutoCancelEnabled}
                    onChange={(e) => updateField('bankTransferAutoCancelEnabled', e.target.checked)}
                    className="h-4 w-4 accent-pe-rose"
                  />
                  <span className="font-sans text-[0.8rem] text-pe-charcoal">Cancelación automática</span>
                </label>

                <label className="flex flex-col gap-1">
                  <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
                    Minutos de espera
                  </span>
                  <input
                    type="number"
                    min={5}
                    max={1440}
                    value={form.bankTransferAutoCancelTimeoutMinutes}
                    onChange={(e) => updateField('bankTransferAutoCancelTimeoutMinutes', Number(e.target.value))}
                    className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                  />
                  <span className="font-sans text-[0.7rem] text-pe-muted">Entre 5 y 1440 min</span>
                </label>

                <label className="flex flex-col gap-1">
                  <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
                    Intervalo cron
                  </span>
                  <input
                    type="text"
                    value={form.bankTransferAutoCancelCron}
                    onChange={(e) => updateField('bankTransferAutoCancelCron', e.target.value)}
                    className="border border-pe-black/15 px-3 py-2 font-mono text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                    placeholder="0 */15 * * * *"
                  />
                  <span className="font-sans text-[0.7rem] text-pe-muted">Expresión Spring cron (6 campos)</span>
                </label>
              </div>
            </div>
          </div>
        )}

        {form.paymentMethodGatewayEnabled && (
          <div className="mt-4 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-3">
            <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
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
                      <span className="block text-[0.72rem] text-pe-muted">{option.subtitle}</span>
                    </span>
                  </label>
                );
              })}
            </div>

            {hasMercadoPagoSelected && (
              <div className="mt-4 rounded-xs border border-pe-black/10 bg-pe-white px-3 py-3">
                <p className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">
                  Configuracion Mercado Pago
                </p>
                <p className="mt-1 font-sans text-[0.72rem] text-pe-muted">
                  Se usa para crear sesiones de checkout y validar webhooks. Si dejas campos vacios, se usa fallback desde .env.
                </p>

                <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">API base URL</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpApiBaseUrl}
                      onChange={(e) => updateField('paymentGatewayMpApiBaseUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="https://api.mercadopago.com"
                    />
                  </label>

                  <label className="flex flex-col gap-1 md:col-span-2">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nuevo Access Token</span>
                    <input
                      type="password"
                      value={form.paymentGatewayMpAccessToken}
                      onChange={(e) => updateField('paymentGatewayMpAccessToken', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="APP_USR-xxxxxxxxxxxxxxxx"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Back URL exito</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpSuccessUrl}
                      onChange={(e) => updateField('paymentGatewayMpSuccessUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Back URL pendiente</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpPendingUrl}
                      onChange={(e) => updateField('paymentGatewayMpPendingUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Back URL fallo</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpFailureUrl}
                      onChange={(e) => updateField('paymentGatewayMpFailureUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="https://tudominio.com/es/account?tab=orders"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Notification URL</span>
                    <input
                      type="url"
                      value={form.paymentGatewayMpNotificationUrl}
                      onChange={(e) => updateField('paymentGatewayMpNotificationUrl', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="https://tudominio.com/api/payments/webhooks/gateway/mercadopago"
                    />
                  </label>

                  <label className="flex flex-col gap-1 md:col-span-2">
                    <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nuevo Webhook Token (opcional)</span>
                    <input
                      type="password"
                      value={form.paymentGatewayMpWebhookToken}
                      onChange={(e) => updateField('paymentGatewayMpWebhookToken', e.target.value)}
                      className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                      placeholder="token-seguro-opcional"
                    />
                  </label>
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-4">
                  <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
                    <input
                      type="checkbox"
                      checked={form.clearPaymentGatewayMpAccessToken}
                      onChange={(e) => updateField('clearPaymentGatewayMpAccessToken', e.target.checked)}
                      className="h-4 w-4 accent-pe-rose"
                    />
                    Limpiar access token guardado
                  </label>

                  <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
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
                  <Icon size={14} className={isActive ? 'text-pe-rose-ink' : 'text-pe-muted'} />
                  <span className="font-sans text-[0.72rem] uppercase tracking-[0.14em] text-pe-charcoal">{option.label}</span>
                </div>
                <p className="mt-2 font-sans text-[0.7rem] text-pe-muted leading-relaxed">{option.subtitle}</p>
              </button>
            );
          })}
        </div>

        <div className="mt-3 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-2">
          <span className="font-sans text-[0.72rem] text-pe-muted">
            Activo ahora: <strong>{selectedMediaStorageProvider?.label ?? form.mediaStorageProvider}</strong>
          </span>
        </div>

        {isLegacyAdmin && (
        <div className="mt-5 border border-pe-black/10 bg-pe-offwhite/50 p-3 sm:p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
                Modelos del hero (home)
              </p>
              <p className="mt-1 font-sans text-[0.72rem] text-pe-muted">
                Arrastra y suelta 2 imagenes propias (izquierda/derecha). Cada slot sobrescribe la anterior.
              </p>
            </div>
            <button
              type="button"
              onClick={() => void loadHeroModels()}
              disabled={heroLoading}
              className="inline-flex items-center gap-1 border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.62rem] uppercase tracking-[0.14em] text-pe-muted hover:border-pe-rose hover:text-pe-rose-ink transition-colors disabled:opacity-50"
            >
              {heroLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
              Recargar
            </button>
          </div>

          <div className="mt-3 grid grid-cols-1 lg:grid-cols-2 gap-3">
            {(['left', 'right'] as HeroModelSlot[]).map((slot) => {
              const data = slot === 'left' ? heroModels?.left : heroModels?.right;
              const fallbackUrl = slot === 'left'
                ? '/api/media/hero-models/hero-left.png'
                : '/api/media/hero-models/hero-right.png';
              const previewUrl = `${data?.url ?? fallbackUrl}?v=${data?.updatedAt ?? 0}`;
              return (
                <article key={slot} className="border border-pe-black/10 bg-pe-white p-3">
                  <p className="font-sans text-[0.62rem] uppercase tracking-[0.14em] text-pe-muted">
                    {heroSlotLabel(slot)}
                  </p>
                  <div className="mt-2 h-36 w-full overflow-hidden border border-pe-black/10 bg-pe-black/3">
                    <img
                      src={previewUrl}
                      alt={heroSlotLabel(slot)}
                      className="h-full w-full object-cover"
                      loading="lazy"
                    />
                  </div>
                  <p className="mt-2 font-sans text-[0.68rem] text-pe-muted">
                    Actualizada: {formatEpochTimestamp(data?.updatedAt)}
                  </p>
                  <div className="mt-2">
                    <ImageDropzone
                      label={heroUploadSlot === slot ? 'Subiendo...' : 'Arrastra o selecciona imagen'}
                      folder="hero-models"
                      value={previewUrl}
                      allowClear={false}
                      preserveOriginalFile
                      token={effectiveToken ?? ''}
                      customUpload={async (file) => {
                        const saved = await handleHeroUpload(slot, file);
                        return `${saved.url ?? fallbackUrl}?v=${saved.updatedAt ?? Date.now()}`;
                      }}
                      onUpload={() => {}}
                    />
                  </div>
                </article>
              );
            })}
          </div>
        </div>
        )}

        {isLegacyAdmin && (
        <div className="pt-4 border-t border-pe-black/8">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted mb-2">
            Migración de imágenes
          </p>
          <p className="font-sans text-[0.72rem] text-pe-muted mb-3">
            Descarga las imágenes de categorías desde URLs externas al almacenamiento configurado.
            Solo procesa imágenes que aún no estén almacenadas localmente.
          </p>
          <button
            type="button"
            onClick={handleMigrateCategories}
            disabled={migrating}
            className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose-ink transition-colors disabled:opacity-50"
          >
            {migrating ? <Loader2 size={13} className="animate-spin" /> : null}
            {migrating ? 'Migrando...' : 'Migrar imágenes de categorías'}
          </button>
          {migrateResult && (
            <p className={`font-sans text-[0.72rem] mt-2 ${migrateResult.failed === -1 ? 'text-red-500' : 'text-pe-muted'}`}>
              {migrateResult.failed === -1
                ? 'Error al ejecutar la migración.'
                : `Migradas: ${migrateResult.migrated} · Fallidas: ${migrateResult.failed}`}
            </p>
          )}
        </div>
        )}

        {isLegacyAdmin && (
        <div className="pt-4 border-t border-pe-black/8">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted mb-2">
            Optimización de imágenes existentes
          </p>
          <p className="font-sans text-[0.72rem] text-pe-muted mb-3">
            Reescribe en disco las imágenes pesadas ya almacenadas (productos + categorías + resto).
            Reencodea JPEG y convierte PNG a JPEG, actualizando referencias en la base de datos.
            Idempotente: ejecutar de nuevo solo procesa lo que aún se pueda achicar. Pensado para corrida única tras la migración inicial.
          </p>
          <button
            type="button"
            onClick={handleOptimizeAll}
            disabled={optimizing}
            className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose-ink transition-colors disabled:opacity-50"
          >
            {optimizing ? <Loader2 size={13} className="animate-spin" /> : null}
            {optimizing ? 'Optimizando...' : 'Optimizar imágenes existentes'}
          </button>
          {optimizeError && (
            <p className="font-sans text-[0.72rem] mt-2 text-red-500">{optimizeError}</p>
          )}
          {optimizeResult && (
            <div className="mt-2 space-y-1 font-sans text-[0.72rem] text-pe-muted">
              <p>
                <strong>Productos:</strong> procesadas {optimizeResult.products.processed} ·
                renombradas {optimizeResult.products.renamed} · saltadas {optimizeResult.products.skipped} ·
                fallidas {optimizeResult.products.failed} · ahorro {formatBytes(optimizeResult.products.bytesSaved)}
              </p>
              <p>
                <strong>Categorías:</strong> procesadas {optimizeResult.categories.processed} ·
                renombradas {optimizeResult.categories.renamed} · saltadas {optimizeResult.categories.skipped} ·
                fallidas {optimizeResult.categories.failed} · ahorro {formatBytes(optimizeResult.categories.bytesSaved)}
              </p>
              <p>
                <strong>Otros:</strong> procesadas {optimizeResult.others.processed} ·
                saltadas {optimizeResult.others.skipped} · fallidas {optimizeResult.others.failed} ·
                ahorro {formatBytes(optimizeResult.others.bytesSaved)}
              </p>
            </div>
          )}
        </div>
        )}

        {isLegacyAdmin && (
        <div className="pt-4 border-t border-pe-black/8">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted mb-2">
            Redimensionar imagenes de productos y categorias (15 cm)
          </p>
          <p className="font-sans text-[0.72rem] text-pe-muted mb-3">
            Ajusta el lado mayor de cada imagen a 15 cm (1772 px), conservando la relacion de aspecto.
          </p>
          <button
            type="button"
            onClick={handleResizeTo15cm}
            disabled={resizingTo15cm}
            className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose-ink transition-colors disabled:opacity-50"
          >
            {resizingTo15cm ? <Loader2 size={13} className="animate-spin" /> : null}
            {resizingTo15cm ? 'Redimensionando...' : 'Redimensionar productos y categorias'}
          </button>
          {resizeTo15cmError && (
            <p className="font-sans text-[0.72rem] mt-2 text-red-500">{resizeTo15cmError}</p>
          )}
          {resizeTo15cmResult && (
            <p className="font-sans text-[0.72rem] mt-2 text-pe-muted">
              Procesadas {resizeTo15cmResult.processed} · Redimensionadas {resizeTo15cmResult.resized} ·
              Saltadas {resizeTo15cmResult.skipped} · Fallidas {resizeTo15cmResult.failed} ·
              objetivo {resizeTo15cmResult.targetLongSidePx}px.
            </p>
          )}
        </div>
        )}
      </section>
      )}

      {activeSettingsTab === 'media' && hasS3CompatibleStorage && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">Configuracion S3 compatible</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            Compatible con proveedores tipo S3/S2 (AWS S3, MinIO, Cloudflare R2, Wasabi, etc).
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Endpoint</span>
              <input
                type="url"
                value={form.mediaS3Endpoint}
                onChange={(e) => updateField('mediaS3Endpoint', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="https://s3.amazonaws.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Region</span>
              <input
                type="text"
                value={form.mediaS3Region}
                onChange={(e) => updateField('mediaS3Region', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="us-east-1"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Bucket</span>
              <input
                type="text"
                value={form.mediaS3Bucket}
                onChange={(e) => updateField('mediaS3Bucket', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="pilarestilo-media"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Access Key ID</span>
              <input
                type="text"
                value={form.mediaS3AccessKeyId}
                onChange={(e) => updateField('mediaS3AccessKeyId', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="AKIA..."
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nuevo Secret Access Key</span>
              <input
                type="password"
                value={form.mediaS3SecretKey}
                onChange={(e) => updateField('mediaS3SecretKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Base URL publica (opcional)</span>
              <input
                type="url"
                value={form.mediaS3PublicBaseUrl}
                onChange={(e) => updateField('mediaS3PublicBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="https://cdn.tu-dominio.com"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
              <input
                type="checkbox"
                checked={form.mediaS3PathStyleEnabled}
                onChange={(e) => updateField('mediaS3PathStyleEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              Path style habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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

      {activeSettingsTab === 'tributarios' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Datos tributarios</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
          Identidad de la tienda ante el SII. Es lo que una boleta declara sobre quien la emite.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">RUT</span>
            <input
              type="text"
              value={form.taxPayerRut}
              onChange={(e) => updateField('taxPayerRut', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="76.543.210-K"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Razon social</span>
            <input
              type="text"
              value={form.taxBusinessName}
              onChange={(e) => updateField('taxBusinessName', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="Comercializadora Pilar Estilo SpA"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Giro</span>
            <input
              type="text"
              value={form.taxBusinessActivity}
              onChange={(e) => updateField('taxBusinessActivity', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="Venta al por menor de prendas de vestir"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Codigo Acteco</span>
            <input
              type="text"
              value={form.taxActecoCode}
              onChange={(e) => updateField('taxActecoCode', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="477101"
            />
          </label>

          <label className="flex flex-col gap-1 md:col-span-2">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Direccion</span>
            <input
              type="text"
              value={form.taxAddress}
              onChange={(e) => updateField('taxAddress', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="Av. Santa Teresa 1234"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Comuna</span>
            <input
              type="text"
              value={form.taxCommune}
              onChange={(e) => updateField('taxCommune', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="Los Andes"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Ciudad</span>
            <input
              type="text"
              value={form.taxCity}
              onChange={(e) => updateField('taxCity', e.target.value)}
              className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              placeholder="Los Andes"
            />
          </label>
        </div>

        <div className="mt-4 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-3">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted mb-2">
            Impuesto y emision
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">IVA (%)</span>
              <input
                type="number"
                min={0}
                max={100}
                step="0.01"
                value={form.taxVatRate}
                onChange={(e) => updateField('taxVatRate', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              />
              <span className="font-sans text-[0.68rem] text-pe-muted">
                Se guarda en cada venta al crearse. Cambiarlo no altera las ventas ya hechas.
              </span>
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Emision</span>
              <select
                value={form.taxDocumentProvider}
                onChange={(e) => updateField('taxDocumentProvider', e.target.value as FormState['taxDocumentProvider'])}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
              >
                <option value="MANUAL">Manual (eBoleta del SII)</option>
                <option value="TUU">TUU</option>
                <option value="OPENFACTURA">OpenFactura</option>
              </select>
              <span className="font-sans text-[0.68rem] text-pe-muted">
                Hoy solo Manual esta implementado: el folio se escribe a mano en Ventas.
              </span>
            </label>
          </div>

          <label className="mt-3 flex items-start gap-2">
            <input
              type="checkbox"
              checked={form.taxDocumentRequiredBeforeDispatch}
              onChange={(e) => updateField('taxDocumentRequiredBeforeDispatch', e.target.checked)}
              className="h-4 w-4 accent-pe-rose mt-0.5"
            />
            <span className="font-sans text-[0.78rem] text-pe-charcoal">
              Exigir boleta antes de despachar
              <span className="block text-[0.68rem] text-pe-muted">
                Una venta pagada sin boleta no se puede tomar ni despachar. Desactivarlo permite que
                salga mercaderia sin documento.
              </span>
            </span>
          </label>
        </div>
      </section>
      )}

      {activeSettingsTab === 'shipping' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Envios</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
          Configura zonas de despacho, comunas cubiertas, couriers y modalidad de pago de envio.
        </p>

        <div className="mt-5 rounded-xs border border-pe-rose/20 bg-pe-cream/40 px-4 py-3">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-rose-ink mb-1">
            Modalidad de pago envio
          </p>
          <p className="font-sans text-[0.84rem] text-pe-black font-medium">
            Envio por pagar (cliente paga al retirar en sucursal del courier)
          </p>
          <p className="mt-1 font-sans text-[0.7rem] text-pe-muted">
            Aplicada globalmente a todas las zonas. Tienda despacha desde Los Andes.
          </p>
        </div>

        <div className="mt-6">
          <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted mb-3">
            Zonas de envio
          </p>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
            {form.shippingZones.map((zone, idx) => (
              <article
                key={zone.code}
                className={`relative border bg-pe-white p-4 transition-colors ${
                  zone.active ? 'border-pe-black/15' : 'border-pe-black/10 opacity-60'
                }`}
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="font-sans text-[0.6rem] tracking-[0.18em] uppercase text-pe-rose-ink font-semibold">
                    {zone.code}
                  </span>
                  <label className="inline-flex items-center gap-1.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={zone.active}
                      onChange={(e) => {
                        const next = [...form.shippingZones];
                        next[idx] = { ...zone, active: e.target.checked };
                        updateField('shippingZones', next);
                      }}
                      className="w-3.5 h-3.5 accent-pe-rose"
                      aria-label={`Zona ${zone.code} activa`}
                    />
                    <span className="font-sans text-[0.62rem] text-pe-muted">
                      {zone.active ? 'Activa' : 'Inactiva'}
                    </span>
                  </label>
                </div>

                <div className="space-y-2">
                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.6rem] uppercase tracking-[0.14em] text-pe-muted">
                      Titulo (ES)
                    </span>
                    <input
                      type="text"
                      value={zone.titleEs}
                      onChange={(e) => {
                        const next = [...form.shippingZones];
                        next[idx] = { ...zone, titleEs: e.target.value };
                        updateField('shippingZones', next);
                      }}
                      className="border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                    />
                  </label>

                  <label className="flex flex-col gap-1">
                    <span className="font-sans text-[0.6rem] uppercase tracking-[0.14em] text-pe-muted">
                      Titulo (EN)
                    </span>
                    <input
                      type="text"
                      value={zone.titleEn}
                      onChange={(e) => {
                        const next = [...form.shippingZones];
                        next[idx] = { ...zone, titleEn: e.target.value };
                        updateField('shippingZones', next);
                      }}
                      className="border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                    />
                  </label>

                  <div className="grid grid-cols-2 gap-2">
                    <label className="flex flex-col gap-1">
                      <span className="font-sans text-[0.6rem] uppercase tracking-[0.14em] text-pe-muted">
                        ETA (ES)
                      </span>
                      <input
                        type="text"
                        value={zone.etaEs}
                        onChange={(e) => {
                          const next = [...form.shippingZones];
                          next[idx] = { ...zone, etaEs: e.target.value };
                          updateField('shippingZones', next);
                        }}
                        className="border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                        placeholder="24-48 hs"
                      />
                    </label>
                    <label className="flex flex-col gap-1">
                      <span className="font-sans text-[0.6rem] uppercase tracking-[0.14em] text-pe-muted">
                        ETA (EN)
                      </span>
                      <input
                        type="text"
                        value={zone.etaEn}
                        onChange={(e) => {
                          const next = [...form.shippingZones];
                          next[idx] = { ...zone, etaEn: e.target.value };
                          updateField('shippingZones', next);
                        }}
                        className="border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                        placeholder="24-48h"
                      />
                    </label>
                  </div>

                  <div className="pt-1">
                    <span className="font-sans text-[0.6rem] uppercase tracking-[0.14em] text-pe-muted mb-1.5 block">
                      Comunas
                    </span>
                    <div className="flex flex-wrap gap-1.5 mb-2">
                      {zone.comunas.map((comuna) => (
                        <span
                          key={comuna}
                          className="inline-flex items-center gap-1 border border-pe-rose/30 bg-pe-rose/5 px-2 py-1 font-sans text-[0.68rem] text-pe-charcoal"
                        >
                          {comuna}
                          <button
                            type="button"
                            aria-label={`Quitar ${comuna}`}
                            onClick={() => {
                              const next = [...form.shippingZones];
                              next[idx] = { ...zone, comunas: zone.comunas.filter((c) => c !== comuna) };
                              updateField('shippingZones', next);
                            }}
                            className="text-pe-muted hover:text-pe-rose-ink transition-colors leading-none text-[0.85rem]"
                          >
                            x
                          </button>
                        </span>
                      ))}
                      {zone.comunas.length === 0 && (
                        <span className="font-sans text-[0.65rem] text-pe-muted italic">
                          Sin comunas
                        </span>
                      )}
                    </div>
                    <input
                      type="text"
                      value={shippingCommuneSearch[zone.code] ?? ''}
                      placeholder="Buscar comuna..."
                      onChange={(e) => {
                        void handleShippingCommuneSearch(zone.code, e.target.value);
                      }}
                      className="w-full border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.75rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                    />
                    {shippingCommuneLoading[zone.code] && (
                      <p className="mt-1 font-sans text-[0.68rem] text-pe-muted">Buscando comunas...</p>
                    )}
                    {(shippingCommuneResults[zone.code] ?? []).length > 0 && (
                      <ul className="mt-2 border border-pe-black/10 bg-pe-white max-h-44 overflow-y-auto">
                        {(shippingCommuneResults[zone.code] ?? []).map((option) => (
                          <li key={`${zone.code}-${option.id}`}>
                            <button
                              type="button"
                              onClick={() => {
                                addCommuneToZone(idx, option.name);
                                setShippingCommuneSearch((prev) => ({ ...prev, [zone.code]: '' }));
                                setShippingCommuneResults((prev) => ({ ...prev, [zone.code]: [] }));
                              }}
                              className="w-full text-left px-2.5 py-2 font-sans text-[0.74rem] text-pe-charcoal hover:bg-pe-cream/35 transition-colors"
                            >
                              {option.name}
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>

        <div className="mt-6 pt-5 border-t border-pe-black/8">
          <div className="flex items-center justify-between mb-3">
            <p className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
              Couriers de despacho
            </p>
            <button
              type="button"
              onClick={() => {
                updateField('shippingCouriers', [
                  ...form.shippingCouriers,
                  { id: `courier-${Date.now()}`, name: '', logoUrl: null, active: true },
                ]);
              }}
              className="font-sans text-[0.62rem] tracking-[0.1em] uppercase text-pe-rose-ink hover:text-pe-rose-ink transition-colors border border-pe-rose/30 px-2.5 py-1"
            >
              + Agregar courier
            </button>
          </div>

          <div className="space-y-2">
            {form.shippingCouriers.map((courier, idx) => (
              <article
                key={courier.id || idx}
                className={`grid grid-cols-1 sm:grid-cols-12 gap-2 items-center border bg-pe-white p-3 ${
                  courier.active ? 'border-pe-black/12' : 'border-pe-black/8 opacity-60'
                }`}
              >
                <input
                  type="text"
                  value={courier.name}
                  placeholder="Nombre courier"
                  onChange={(e) => {
                    const next = [...form.shippingCouriers];
                    const newName = e.target.value;
                    next[idx] = { ...courier, name: newName, id: courier.id || slugifyCourierId(newName) };
                    updateField('shippingCouriers', next);
                  }}
                  className="sm:col-span-4 border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                />
                <input
                  type="url"
                  value={courier.logoUrl ?? ''}
                  placeholder="Logo URL (opcional)"
                  onChange={(e) => {
                    const next = [...form.shippingCouriers];
                    next[idx] = { ...courier, logoUrl: e.target.value.trim() || null };
                    updateField('shippingCouriers', next);
                  }}
                  className="sm:col-span-5 border border-pe-black/15 px-2.5 py-1.5 font-sans text-[0.78rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                />
                <label className="sm:col-span-2 inline-flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={courier.active}
                    onChange={(e) => {
                      const next = [...form.shippingCouriers];
                      next[idx] = { ...courier, active: e.target.checked };
                      updateField('shippingCouriers', next);
                    }}
                    className="w-3.5 h-3.5 accent-pe-rose"
                    aria-label={`Courier ${courier.name} activo`}
                  />
                  <span className="font-sans text-[0.66rem] text-pe-muted">
                    {courier.active ? 'Activo' : 'Pausado'}
                  </span>
                </label>
                <button
                  type="button"
                  aria-label={`Quitar courier ${courier.name}`}
                  onClick={() => {
                    const next = form.shippingCouriers.filter((_, i) => i !== idx);
                    updateField('shippingCouriers', next);
                  }}
                  className="sm:col-span-1 font-sans text-[0.62rem] tracking-[0.08em] uppercase text-pe-muted hover:text-pe-rose-ink transition-colors border border-pe-black/12 hover:border-pe-rose/40 px-2 py-1.5"
                >
                  Quitar
                </button>
              </article>
            ))}
            {form.shippingCouriers.length === 0 && (
              <p className="font-sans text-[0.72rem] text-pe-muted italic">
                No hay couriers configurados.
              </p>
            )}
          </div>
        </div>
      </section>
      )}

      {activeSettingsTab === 'notifications' && (
      <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
        <h2 className="font-display text-2xl text-pe-black font-light">Canales de notificacion</h2>
        <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
          Cada mensaje transaccional (pedidos, pagos y envios) sale por todos los canales activos.
          Puedes tener varios: el correo con el detalle de la compra es obligatorio por ley, y
          WhatsApp llega antes.
        </p>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-2.5">
          {PROVIDER_OPTIONS.map((option) => {
            const Icon = option.icon;
            const isActive = form.notificationProviders.includes(option.value);
            const isLastActive = isActive && form.notificationProviders.length === 1;
            return (
              <button
                key={option.value}
                type="button"
                role="switch"
                aria-checked={isActive}
                onClick={() => toggleNotificationProvider(option.value)}
                title={isLastActive ? 'Es el unico canal activo; activa otro antes de apagarlo.' : undefined}
                className={[
                  'text-left border px-3 py-3 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose',
                  isActive
                    ? 'border-pe-rose bg-pe-rose/10'
                    : 'border-pe-black/10 hover:border-pe-rose/45 hover:bg-pe-rose/10',
                  isLastActive ? 'cursor-not-allowed' : '',
                ].join(' ')}
              >
                <div className="inline-flex items-center gap-2">
                  <Icon size={14} className={isActive ? 'text-pe-rose-ink' : 'text-pe-muted'} />
                  <span className="font-sans text-[0.72rem] uppercase tracking-[0.14em] text-pe-charcoal">{option.label}</span>
                  {/* Never colour alone: the state is spelled out. */}
                  <span className="font-sans text-[0.58rem] uppercase tracking-[0.12em] text-pe-muted">
                    {isActive ? 'Activo' : 'Apagado'}
                  </span>
                </div>
                <p className="mt-2 font-sans text-[0.7rem] text-pe-muted leading-relaxed">{option.subtitle}</p>
              </button>
            );
          })}
        </div>

        <div className="mt-3 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-2">
          <span className="font-sans text-[0.72rem] text-pe-muted">
            Activos ahora:{' '}
            <strong>
              {form.notificationProviders
                .map((value) => PROVIDER_OPTIONS.find((option) => option.value === value)?.label ?? value)
                .join(' + ')}
            </strong>
          </span>
        </div>
      </section>
      )}

      {activeSettingsTab === 'notifications' && hasProviderSimulated && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">WhatsApp Simulado</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            Este modo no envia mensajes reales. Deja trazas en logs para pruebas.
          </p>
          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Destino simulado</span>
              <input
                type="text"
                value={form.whatsappSimulatedTo}
                onChange={(e) => updateField('whatsappSimulatedTo', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="+56900000000"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Alias remitente</span>
              <input
                type="text"
                value={form.whatsappSimulatedSender}
                onChange={(e) => updateField('whatsappSimulatedSender', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Pilar Estilo"
              />
            </label>
          </div>
        </section>
      )}

      {activeSettingsTab === 'notifications' && hasProviderRequiringTwilio && (
        <section className="border border-pe-black/10 bg-pe-white p-4 sm:p-5">
          <h2 className="font-display text-2xl text-pe-black font-light">WhatsApp Twilio</h2>
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            Configura credenciales y numeros en formato internacional.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">API base URL</span>
              <input
                type="url"
                value={form.whatsappTwilioApiBaseUrl}
                onChange={(e) => updateField('whatsappTwilioApiBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="https://api.twilio.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Account SID</span>
              <input
                type="text"
                value={form.whatsappTwilioAccountSid}
                onChange={(e) => updateField('whatsappTwilioAccountSid', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="ACxxxxxxxxxxxxxxxx"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">WhatsApp From</span>
              <input
                type="text"
                value={form.whatsappTwilioFrom}
                onChange={(e) => updateField('whatsappTwilioFrom', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="whatsapp:+14155238886"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Fallback destino</span>
              <input
                type="text"
                value={form.whatsappTwilioToFallback}
                onChange={(e) => updateField('whatsappTwilioToFallback', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="+56900000000"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Alias remitente</span>
              <input
                type="text"
                value={form.whatsappTwilioSenderAlias}
                onChange={(e) => updateField('whatsappTwilioSenderAlias', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Pilar Estilo"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nuevo Auth Token</span>
              <input
                type="password"
                value={form.whatsappTwilioAuthToken}
                onChange={(e) => updateField('whatsappTwilioAuthToken', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            Usa API key cifrada y correo remitente para notificaciones transaccionales.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">API base URL</span>
              <input
                type="url"
                value={form.sendgridApiBaseUrl}
                onChange={(e) => updateField('sendgridApiBaseUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="https://api.sendgrid.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Correo remitente</span>
              <input
                type="email"
                value={form.sendgridFromEmail}
                onChange={(e) => updateField('sendgridFromEmail', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="ventas@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nombre remitente</span>
              <input
                type="text"
                value={form.sendgridSenderName}
                onChange={(e) => updateField('sendgridSenderName', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Pilar Estilo"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Fallback destino</span>
              <input
                type="email"
                value={form.sendgridToFallback}
                onChange={(e) => updateField('sendgridToFallback', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="alerts@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nueva API key</span>
              <input
                type="password"
                value={form.sendgridApiKey}
                onChange={(e) => updateField('sendgridApiKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Deja vacio para mantener la actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            La password se guarda cifrada en base de datos y no se muestra en texto plano.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Host SMTP</span>
              <input
                type="text"
                value={form.smtpHost}
                onChange={(e) => updateField('smtpHost', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="smtp.gmail.com"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Puerto SMTP</span>
              <input
                type="text"
                inputMode="numeric"
                value={form.smtpPort}
                onChange={(e) => updateField('smtpPort', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="587"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Usuario SMTP</span>
              <input
                type="text"
                value={form.smtpUsername}
                onChange={(e) => updateField('smtpUsername', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="usuario_smtp"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Correo remitente</span>
              <input
                type="email"
                value={form.smtpFromEmail}
                onChange={(e) => updateField('smtpFromEmail', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="ventas@pilarestilo.com"
              />
            </label>

            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nueva password SMTP</span>
              <input
                type="password"
                value={form.smtpPassword}
                onChange={(e) => updateField('smtpPassword', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Deja vacio para mantener la actual"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
              <input
                type="checkbox"
                checked={form.smtpAuthEnabled}
                onChange={(e) => updateField('smtpAuthEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              SMTP auth habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
              <input
                type="checkbox"
                checked={form.smtpStarttlsEnabled}
                onChange={(e) => updateField('smtpStarttlsEnabled', e.target.checked)}
                className="h-4 w-4 accent-pe-rose"
              />
              STARTTLS habilitado
            </label>

            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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
          <p className="mt-1 font-sans text-[0.74rem] text-pe-muted">
            Configura webhook y token para delegar notificaciones a flujos n8n.
            Si dejas campos vacios, el backend usa fallback desde variables de entorno.
          </p>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1 md:col-span-2">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Webhook URL</span>
              <input
                type="url"
                value={form.n8nWebhookUrl}
                onChange={(e) => updateField('n8nWebhookUrl', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="https://n8n.tudominio.com/webhook/pilar-notifications"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Header token</span>
              <input
                type="text"
                value={form.n8nTokenHeaderName}
                onChange={(e) => updateField('n8nTokenHeaderName', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="X-PE-N8N-TOKEN"
              />
            </label>

            <label className="flex flex-col gap-1">
              <span className="font-sans text-[0.66rem] uppercase tracking-[0.16em] text-pe-muted">Nuevo API key/token</span>
              <input
                type="password"
                value={form.n8nApiKey}
                onChange={(e) => updateField('n8nApiKey', e.target.value)}
                className="border border-pe-black/15 px-3 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:border-pe-rose/45 focus:outline-hidden"
                placeholder="Deja vacio para mantener el actual"
              />
            </label>
          </div>

          <div className="mt-4">
            <label className="inline-flex items-center gap-2 font-sans text-[0.74rem] text-pe-muted">
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
          <div className="mt-3 rounded-xs border border-pe-black/10 bg-pe-offwhite px-3 py-2">
            <span className="font-sans text-[0.72rem] text-pe-muted">
              Tip: cada cliente puede elegir su canal preferido (WhatsApp/Correo/Ambos) en Mi Cuenta y n8n puede enrutar en base a ese dato.
              Si no completas estos campos, se usan fallback desde <span className="font-mono text-[0.7rem]">NOTIFICATION_N8N_*</span>.
            </span>
          </div>
        </section>
      )}
      </fieldset>

      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={saving || !canUpdateSettings}
          className="inline-flex items-center gap-2 bg-pe-black px-4 py-2.5 font-sans text-[0.68rem] uppercase tracking-[0.16em] text-pe-offwhite hover:bg-[#3A3A3A] disabled:opacity-50"
        >
          {saving ? <Loader2 size={13} className="animate-spin" /> : <Save size={13} />}
          {canUpdateSettings ? 'Guardar configuracion' : 'Solo lectura'}
        </button>
      </div>
    </div>
  );
}

