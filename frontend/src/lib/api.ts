// All API calls for Pilar Estilo go through this module only.

const PUBLIC_API_BASE: string =
  import.meta.env.PUBLIC_API_BASE_URL ?? '/api';

const INTERNAL_API_BASE: string =
  import.meta.env.INTERNAL_API_BASE_URL ?? 'http://backend:8080/api';

const API_BASE: string =
  typeof window === 'undefined' ? INTERNAL_API_BASE : PUBLIC_API_BASE;

// ─── Types ──────────────────────────────────────────────────────────────────

export interface MoneyDto {
  amount: number;
  currency: string;
}

export interface SizeStockDto {
  size: string;
  stock: number;
}

export interface ProductVariantDto {
  color: string;
  size: string;
  stock: number;
}

export interface ProductDto {
  id: string;
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  avgRating?: number;
  reviewCount?: number;
  shippingOriginZone?: 'LOCAL' | 'REGIONAL' | 'NACIONAL';
  sizeStocks?: SizeStockDto[];
  categorySlugs?: string[];
  variants?: ProductVariantDto[];
}

export interface WishlistDto {
  customerId: string;
  productIds: string[];
}

export interface WishlistShareLinkDto {
  token: string | null;
  enabled: boolean;
}

export interface SharedWishlistDto {
  token: string;
  productIds: string[];
}

export interface ProductFilter {
  condition?: 'NEW' | 'USED';
  brand?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  active?: boolean;
  inStock?: boolean;
  page?: number;
  size?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface PaymentDto {
  id: string;
  orderId: string;
  method: string;
  status: string;
  proofReference?: string;
  transferAccountHolderName?: string;
  transferAccountEmail?: string;
  transferAccountNumber?: string;
  transferBankName?: string;
  transferAccountType?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface PaymentGatewayCheckoutDto {
  paymentId: string;
  orderId: string;
  gatewayReference: string;
  checkoutUrl: string;
  expiresAt?: string;
}

export interface OrderItemDto {
  id: string;
  productId: string;
  productName: string;
  unitPrice: MoneyDto;
  quantity: number;
}

export interface OrderDto {
  id: string;
  customerId: string;
  items: OrderItemDto[];
  subtotal: MoneyDto;
  discountAmount: MoneyDto;
  totalAmount: MoneyDto;
  paymentMethod: 'BANK_TRANSFER' | 'CASH_ON_DELIVERY' | 'AGREED_BY_WHATSAPP' | 'STORE_CREDIT' | 'PAYMENT_GATEWAY';
  shippingZoneCode?: ShippingZoneCode | null;
  shippingCourierId?: string | null;
  shippingCourierName?: string | null;
  shippingPaymentMode?: ShippingPaymentMode | null;
  shippingAddressId?: string | null;
  shippingAddressReference?: string | null;
  notes?: string | null;
  status: 'CREATED' | 'PENDING_PAYMENT' | 'PAYMENT_UNDER_REVIEW' | 'PAID' | 'PREPARING_ORDER' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface DispatchHistoryRowDto {
  id: string;
  orderId: string;
  dispatcherId: string | null;
  dispatchedBy: string;
  status: string;
  carrier: string | null;
  trackingCode: string | null;
  scheduledDate?: string | null;
  dispatchedAt?: string | null;
  deliveredAt?: string | null;
  carrierOverrideConfigured?: string | null;
  carrierOverrideSelected?: string | null;
  carrierOverrideBy?: string | null;
  carrierOverrideAt?: string | null;
  notes?: string | null;
  createdAt: string;
  orderCreatedAt?: string | null;
  soldBy: string;
}

export interface AdminUserDto {
  id: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'SELLER' | 'CUSTOMER' | 'SUPERVISOR' | 'ADMINISTRACION' | 'DESPACHADOR';
  active: boolean;
  createdAt: string;
}

export interface AdminUsersFilter {
  role?: AdminUserDto['role'];
  active?: boolean;
}

export interface UpdateAdminUserRequest {
  fullName?: string;
  role?: AdminUserDto['role'];
  active?: boolean;
}

export type NotificationProvider =
  | 'LOG'
  | 'WHATSAPP_SIMULATED'
  | 'WHATSAPP_TWILIO'
  | 'EMAIL_SENDGRID'
  | 'EMAIL_SMTP'
  | 'N8N_WEBHOOK';

export type MediaStorageProvider =
  | 'LOCAL'
  | 'S3_COMPATIBLE';

export type PaymentGatewayProvider =
  | 'MERCADO_PAGO';

export type ShippingZoneCode = 'LOCAL' | 'REGIONAL' | 'NACIONAL';

export type ShippingPaymentMode = 'POR_PAGAR';

export interface ShippingZoneConfig {
  code: ShippingZoneCode;
  titleEs: string;
  titleEn: string;
  etaEs: string;
  etaEn: string;
  comunas: string[];
  active: boolean;
  sortOrder: number;
}

export interface CourierConfig {
  id: string;
  name: string;
  logoUrl: string | null;
  active: boolean;
}

export interface SystemSettingsDto {
  whatsappNumber: string;
  instagramUrl?: string | null;
  facebookUrl?: string | null;
  bankTransferAccountHolder?: string | null;
  bankTransferContactEmail?: string | null;
  bankTransferAccountNumber?: string | null;
  bankTransferBankName?: string | null;
  bankTransferAccountType?: string | null;
  paymentMethodBankTransferEnabled: boolean;
  paymentMethodGatewayEnabled: boolean;
  paymentGatewayProviders: PaymentGatewayProvider[];
  paymentGatewayMpApiBaseUrl?: string | null;
  paymentGatewayMpSuccessUrl?: string | null;
  paymentGatewayMpPendingUrl?: string | null;
  paymentGatewayMpFailureUrl?: string | null;
  paymentGatewayMpNotificationUrl?: string | null;
  paymentGatewayMpAccessTokenConfigured: boolean;
  paymentGatewayMpWebhookTokenConfigured: boolean;
  mediaStorageProvider: MediaStorageProvider;
  mediaS3Endpoint?: string | null;
  mediaS3Region?: string | null;
  mediaS3Bucket?: string | null;
  mediaS3AccessKeyId?: string | null;
  mediaS3SecretKeyConfigured: boolean;
  mediaS3PathStyleEnabled: boolean;
  mediaS3PublicBaseUrl?: string | null;
  notificationProvider: NotificationProvider;
  n8nWebhookUrl?: string | null;
  n8nTokenHeaderName?: string | null;
  n8nApiKeyConfigured: boolean;
  whatsappSimulatedTo?: string | null;
  whatsappSimulatedSender?: string | null;
  whatsappTwilioApiBaseUrl?: string | null;
  whatsappTwilioAccountSid?: string | null;
  whatsappTwilioFrom?: string | null;
  whatsappTwilioToFallback?: string | null;
  whatsappTwilioSenderAlias?: string | null;
  whatsappTwilioAuthTokenConfigured: boolean;
  sendgridApiBaseUrl?: string | null;
  sendgridFromEmail?: string | null;
  sendgridSenderName?: string | null;
  sendgridToFallback?: string | null;
  sendgridApiKeyConfigured: boolean;
  productAiInferDefaultBrand?: string | null;
  productAiInferDefaultCondition?: 'NEW' | 'USED' | null;
  productAiInferBasePrice?: number | null;
  productAiInferListPriceMultiplier?: number | null;
  smtpHost?: string | null;
  smtpPort?: number | null;
  smtpUsername?: string | null;
  smtpFromEmail?: string | null;
  smtpAuthEnabled: boolean;
  smtpStarttlsEnabled: boolean;
  smtpPasswordConfigured: boolean;
  shippingZonesJson?: string | null;
  shippingCouriersJson?: string | null;
  shippingPaymentMode?: ShippingPaymentMode;
  updatedAt?: string;
  updatedBy?: string | null;
}

export interface UpdateSystemSettingsRequest {
  whatsappNumber: string;
  instagramUrl?: string;
  facebookUrl?: string;
  bankTransferAccountHolder?: string;
  bankTransferContactEmail?: string;
  bankTransferAccountNumber?: string;
  bankTransferBankName?: string;
  bankTransferAccountType?: string;
  paymentMethodBankTransferEnabled: boolean;
  paymentMethodGatewayEnabled: boolean;
  paymentGatewayProviders: PaymentGatewayProvider[];
  paymentGatewayMpApiBaseUrl?: string;
  paymentGatewayMpSuccessUrl?: string;
  paymentGatewayMpPendingUrl?: string;
  paymentGatewayMpFailureUrl?: string;
  paymentGatewayMpNotificationUrl?: string;
  paymentGatewayMpAccessToken?: string;
  clearPaymentGatewayMpAccessToken?: boolean;
  paymentGatewayMpWebhookToken?: string;
  clearPaymentGatewayMpWebhookToken?: boolean;
  mediaStorageProvider: MediaStorageProvider;
  mediaS3Endpoint?: string;
  mediaS3Region?: string;
  mediaS3Bucket?: string;
  mediaS3AccessKeyId?: string;
  mediaS3SecretKey?: string;
  clearMediaS3SecretKey?: boolean;
  mediaS3PathStyleEnabled: boolean;
  mediaS3PublicBaseUrl?: string;
  notificationProvider: NotificationProvider;
  n8nWebhookUrl?: string;
  n8nTokenHeaderName?: string;
  n8nApiKey?: string;
  clearN8nApiKey?: boolean;
  whatsappSimulatedTo?: string;
  whatsappSimulatedSender?: string;
  whatsappTwilioApiBaseUrl?: string;
  whatsappTwilioAccountSid?: string;
  whatsappTwilioFrom?: string;
  whatsappTwilioToFallback?: string;
  whatsappTwilioSenderAlias?: string;
  whatsappTwilioAuthToken?: string;
  clearWhatsappTwilioAuthToken?: boolean;
  sendgridApiBaseUrl?: string;
  sendgridFromEmail?: string;
  sendgridSenderName?: string;
  sendgridToFallback?: string;
  sendgridApiKey?: string;
  clearSendgridApiKey?: boolean;
  productAiInferDefaultBrand?: string;
  productAiInferDefaultCondition?: 'NEW' | 'USED';
  productAiInferBasePrice?: number;
  productAiInferListPriceMultiplier?: number;
  smtpHost?: string;
  smtpPort?: number;
  smtpUsername?: string;
  smtpFromEmail?: string;
  smtpPassword?: string;
  clearSmtpPassword?: boolean;
  smtpAuthEnabled: boolean;
  smtpStarttlsEnabled: boolean;
  shippingZonesJson?: string;
  shippingCouriersJson?: string;
  shippingPaymentMode?: ShippingPaymentMode;
}

export interface PublicStoreSettingsDto {
  whatsappNumber: string;
  instagramUrl?: string | null;
  facebookUrl?: string | null;
  supportEmail?: string | null;
  bankTransferAccountHolder?: string | null;
  bankTransferContactEmail?: string | null;
  bankTransferAccountNumber?: string | null;
  bankTransferBankName?: string | null;
  bankTransferAccountType?: string | null;
  paymentMethodBankTransferEnabled: boolean;
  paymentMethodGatewayEnabled: boolean;
  paymentGatewayProviders: PaymentGatewayProvider[];
  shippingZonesJson?: string | null;
  shippingCouriersJson?: string | null;
  shippingPaymentMode?: ShippingPaymentMode;
}

export interface CustomerCreditDto {
  id: string;
  customerId: string;
  balanceAmount: number;
  balanceCurrency: string;
}

export interface CreditMovementDto {
  id: string;
  customerId: string;
  type: 'CREDIT_GRANTED' | 'CREDIT_USED' | 'CREDIT_EXPIRED' | 'CREDIT_ADJUSTED';
  amount: number;
  currency: string;
  reason?: string | null;
  createdAt: string;
}

export type CashMovementType = 'SALE' | 'IN' | 'OUT' | 'REFUND';

export interface CashMovementDto {
  id: string;
  type: CashMovementType;
  amount: number;
  description: string;
  orderId?: string | null;
  recordedAt: string;
  recordedBy: string;
}

export interface CashRegisterDto {
  id: string;
  sellerId: string;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  closedAt?: string | null;
  openingBalance: number;
  closingBalance?: number | null;
  expectedBalance: number;
  difference?: number | null;
  notes?: string | null;
  movements: CashMovementDto[];
}

export interface CashRegisterHistoryFilter {
  status?: 'OPEN' | 'CLOSED';
  from?: string;
  to?: string;
  sellerId?: string;
  page?: number;
  size?: number;
}

export interface CreateOrderItemRequest {
  productId: string;
  quantity: number;
  variantColor?: string;
  variantSize?: string;
}

export interface CreateOrderRequest {
  customerId: string;
  items: CreateOrderItemRequest[];
  paymentMethod: OrderDto['paymentMethod'];
  shippingZoneCode: ShippingZoneCode;
  shippingCourierId: string;
  shippingAddressId: string;
  notes?: string;
  discountCode?: string;
}

export interface CustomerAddressDto {
  id: string;
  customerId: string;
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string | null;
  regionId?: number | null;
  cityId?: number | null;
  communeId?: number | null;
  comuna: string;
  city: string;
  region: string;
  reference?: string | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerAddressRequest {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  regionId: number;
  cityId: number;
  comunaId: number;
  comuna: string;
  city: string;
  region: string;
  reference?: string;
  isDefault?: boolean;
}

export interface UpdateCustomerAddressRequest {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  regionId: number;
  cityId: number;
  comunaId: number;
  comuna: string;
  city: string;
  region: string;
  reference?: string;
}

export interface LocationCommuneDto {
  id: number;
  regionId: number;
  cityId: number;
  name: string;
}

export interface LocationCityDto {
  id: number;
  regionId: number;
  name: string;
  communes: LocationCommuneDto[];
}

export interface LocationRegionDto {
  id: number;
  name: string;
  cities: LocationCityDto[];
}

export interface CreateProductRequest {
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  categoryIds?: string[];
  variants?: ProductVariantDto[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl?: string;
  condition?: 'NEW' | 'USED';
  brand?: string;
  stock?: number;
  active?: boolean;
  categoryIds?: string[];
  variants?: ProductVariantDto[];
}

export interface MediaUploadDto {
  url: string;
  filename: string;
  size: number;
}

export type ProductAiJobStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'ERROR';

export interface ProductAiDraftDto {
  draftId: string;
  productId: string | null;
  status: 'DRAFT' | 'READY' | 'PUBLISHED';
  createdAt: string;
}

export interface ProductAiUploadedAssetDto {
  assetId: string;
  originalUrl: string;
  filename: string;
}

export interface ProductAiUploadResultDto {
  draftId: string;
  uploaded: ProductAiUploadedAssetDto[];
}

export interface ProductAiJobItemDto {
  assetId: string;
  title: string;
  description: string;
  imagePrompt: string;
  processedMasterUrl: string | null;
  processedWebUrl: string | null;
  processedThumbUrl: string | null;
}

export interface ProductAiJobDto {
  jobId: string;
  draftId: string;
  status: ProductAiJobStatus;
  progress: number;
  attempt: number;
  maxAttempts: number;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  items: ProductAiJobItemDto[];
}

export interface ProductAiJobSummaryDto {
  jobId: string;
  draftId: string;
  status: ProductAiJobStatus;
  progress: number;
  attempt: number;
  maxAttempts: number;
  updatedAt: string;
}

export interface ProductAiPublishResultDto {
  productId: string;
  status: 'PUBLISHED';
  publishedAt: string;
}

export interface ProductAiInferenceDto {
  title: string;
  description: string;
  imagePrompt: string;
  engine: string;
  fallbackReason?: string | null;
}

export interface ProductAiImageTransformDto {
  processedMasterUrl: string;
  processedWebUrl: string;
  processedThumbUrl: string;
  provider: string;
  promptUsed: string;
  engine: string;
}

// ─── Fixture Fallback ───────────────────────────────────────────────────────

export const FIXTURE_PRODUCTS: ProductDto[] = [
  {
    id: 'fixture-1',
    name: 'Bolso Chanel Classic Flap',
    description: 'Bolso icónico de Chanel en piel de cordero acolchada. Hardware dorado. En excelente estado de conservación.',
    price: { amount: 850000, currency: 'CLP' },
    listPrice: { amount: 980000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-002.jpg',
    condition: 'USED',
    brand: 'Chanel',
    stock: 1,
    active: true,
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
  },
  {
    id: 'fixture-2',
    name: 'Cinturón Hermès Reversible',
    description: 'Cinturón reversible Hermès con hebilla H en metal dorado. Cuero negro/marrón. Talle 85.',
    price: { amount: 320000, currency: 'CLP' },
    listPrice: { amount: 390000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-006.jpg',
    condition: 'USED',
    brand: 'Hermès',
    stock: 1,
    active: true,
    createdAt: '2026-01-16T10:00:00Z',
    updatedAt: '2026-01-16T10:00:00Z',
  },
  {
    id: 'fixture-3',
    name: 'Zapatillas Gucci Ace',
    description: 'Zapatillas Gucci Ace de cuero blanco con bordado de abeja y flores. Talle 38. Sin uso.',
    price: { amount: 410000, currency: 'CLP' },
    listPrice: { amount: 470000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-003.jpg',
    condition: 'NEW',
    brand: 'Gucci',
    stock: 1,
    active: true,
    createdAt: '2026-01-17T10:00:00Z',
    updatedAt: '2026-01-17T10:00:00Z',
  },
  {
    id: 'fixture-4',
    name: 'Lentes Louis Vuitton My LV',
    description: 'Anteojos de sol Louis Vuitton My LV con montura acetato en negro. Protección UV400.',
    price: { amount: 180000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-004.jpg',
    condition: 'NEW',
    brand: 'Louis Vuitton',
    stock: 2,
    active: true,
    createdAt: '2026-01-18T10:00:00Z',
    updatedAt: '2026-01-18T10:00:00Z',
  },
  {
    id: 'fixture-5',
    name: 'Pañuelo Hermès Carré 90',
    description: 'Pañuelo de seda Hermès 90x90cm. Diseño Jungle Love. Colores vibrantes. En caja original.',
    price: { amount: 95000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-008.jpg',
    condition: 'NEW',
    brand: 'Hermès',
    stock: 3,
    active: true,
    createdAt: '2026-01-19T10:00:00Z',
    updatedAt: '2026-01-19T10:00:00Z',
  },
  {
    id: 'fixture-6',
    name: 'Cartera Prada Saffiano',
    description: 'Cartera mediana Prada en cuero saffiano negro con logo triangular. Compartimentos interiores. Como nueva.',
    price: { amount: 540000, currency: 'CLP' },
    imageUrl: '/api/media/products/product-010.jpg',
    condition: 'USED',
    brand: 'Prada',
    stock: 1,
    active: true,
    createdAt: '2026-01-20T10:00:00Z',
    updatedAt: '2026-01-20T10:00:00Z',
  },
];

// ─── HTTP Helpers ────────────────────────────────────────────────────────────

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`;
  const internalTimeoutMsRaw = Number((import.meta.env as Record<string, unknown>).INTERNAL_API_TIMEOUT_MS ?? 4000);
  const internalTimeoutMs = Number.isFinite(internalTimeoutMsRaw) && internalTimeoutMsRaw > 0
    ? internalTimeoutMsRaw
    : 4000;
  const shouldUseServerTimeout = typeof window === 'undefined' && !init?.signal;
  const controller = shouldUseServerTimeout ? new AbortController() : null;
  const timeoutId = controller
    ? setTimeout(() => controller.abort(), internalTimeoutMs)
    : null;
  const headers = {
    'Content-Type': 'application/json',
    ...(init?.headers ?? {}),
  };
  try {
    const res = await fetch(url, {
      ...init,
      headers,
      signal: controller?.signal ?? init?.signal,
    });
    if (!res.ok) {
      let detail = `API error ${res.status} for ${url}`;
      try {
        const body = await res.json() as { detail?: string; message?: string; error?: string };
        detail = body.detail ?? body.message ?? body.error ?? detail;
      } catch {
        // Keep default detail when body is not JSON.
      }
      throw new Error(detail);
    }
    if (res.status === 204) return undefined as unknown as T;
    return res.json() as Promise<T>;
  } catch (error) {
    if (controller && (error as { name?: string }).name === 'AbortError') {
      throw new Error(`API timeout after ${internalTimeoutMs}ms for ${url}`);
    }
    throw error;
  } finally {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
  }
}

function buildQuery(params: Record<string, unknown>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null);
  if (!entries.length) return '';
  return '?' + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');
}

// ─── Normalizers ─────────────────────────────────────────────────────────────
// Backend serializes Money as flat fields (priceAmount/priceCurrency).
// Frontend types use nested price: { amount, currency }.

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function normalizeProduct(raw: any): ProductDto {
  return {
    ...raw,
    price: raw.price ?? { amount: raw.priceAmount, currency: raw.priceCurrency ?? 'CLP' },
    listPrice: raw.listPrice
      ?? (raw.listPriceAmount != null
        ? { amount: raw.listPriceAmount, currency: raw.listPriceCurrency ?? raw.priceCurrency ?? 'CLP' }
        : undefined),
  };
}

function hasSellableStock(product: ProductDto): boolean {
  if (Number.isFinite(Number(product.stock))) {
    return Number(product.stock) > 0;
  }
  if (Array.isArray(product.variants) && product.variants.length > 0) {
    return product.variants.some((variant) => Number(variant.stock ?? 0) > 0);
  }
  if (Array.isArray(product.sizeStocks) && product.sizeStocks.length > 0) {
    return product.sizeStocks.some((sizeStock) => Number(sizeStock.stock ?? 0) > 0);
  }
  return Number(product.stock ?? 0) > 0;
}

function authHeaders(token?: string): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function toProductMutationBody(data: CreateProductRequest | UpdateProductRequest) {
  const { price, listPrice, ...rest } = data;
  return {
    ...rest,
    ...(price ? { priceAmount: price.amount, priceCurrency: price.currency } : {}),
    ...(listPrice ? { listPriceAmount: listPrice.amount, listPriceCurrency: listPrice.currency } : {}),
  };
}

// ─── API Functions ───────────────────────────────────────────────────────────

export async function getProducts(filter?: ProductFilter): Promise<Page<ProductDto>> {
  try {
    const query = buildQuery((filter ?? {}) as Record<string, unknown>);
    const page = await apiFetch<Page<unknown>>(`/products${query}`);
    const normalized = page.content.map(normalizeProduct);
    const content = filter?.inStock ? normalized.filter(hasSellableStock) : normalized;
    return { ...page, content };
  } catch {
    return {
      content: FIXTURE_PRODUCTS,
      totalElements: FIXTURE_PRODUCTS.length,
      totalPages: 1,
      size: FIXTURE_PRODUCTS.length,
      number: 0,
    };
  }
}

export async function getProduct(id: string): Promise<ProductDto> {
  try {
    const raw = await apiFetch<unknown>(`/products/${encodeURIComponent(id)}`);
    return normalizeProduct(raw);
  } catch {
    const fixture = FIXTURE_PRODUCTS.find((p) => p.id === id);
    if (fixture) return fixture;
    throw new Error(`Product ${id} not found`);
  }
}

export async function getFeaturedProducts(): Promise<ProductDto[]> {
  const query = buildQuery({ active: true, inStock: true, size: 8, page: 0, sort: 'createdAt,desc' });

  try {
    const firstPage = await apiFetch<Page<unknown>>(`/products${query}`);
    return firstPage.content.map(normalizeProduct).filter(hasSellableStock);
  } catch {
    // Retry once for transient upstream hiccups before falling back.
    await new Promise((resolve) => setTimeout(resolve, 180));
    try {
      const secondPage = await apiFetch<Page<unknown>>(`/products${query}`);
      return secondPage.content.map(normalizeProduct).filter(hasSellableStock);
    } catch {
      return FIXTURE_PRODUCTS.slice(0, 8);
    }
  }
}

export async function createProduct(data: CreateProductRequest, token?: string): Promise<ProductDto> {
  const raw = await apiFetch<unknown>('/products', {
    method: 'POST',
    body: JSON.stringify(toProductMutationBody(data)),
    headers: authHeaders(token),
  });
  return normalizeProduct(raw);
}

export async function updateProduct(id: string, data: UpdateProductRequest, token?: string): Promise<ProductDto> {
  const raw = await apiFetch<unknown>(`/products/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(toProductMutationBody(data)),
    headers: authHeaders(token),
  });
  return normalizeProduct(raw);
}

export async function deleteProduct(id: string, token?: string): Promise<void> {
  await apiFetch<void>(`/products/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export async function uploadProductImage(file: File, token: string): Promise<MediaUploadDto> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('folder', 'products');

  const res = await fetch(`${API_BASE}/media/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  });

  if (!res.ok) {
    throw new Error(`Error al subir imagen (${res.status})`);
  }

  return res.json() as Promise<MediaUploadDto>;
}

export async function uploadPaymentProofImage(file: File, token: string): Promise<MediaUploadDto> {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${API_BASE}/media/upload-proof`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  });

  if (!res.ok) {
    let detail = `Error al subir comprobante (${res.status})`;
    try {
      const body = await res.json() as { detail?: string; message?: string };
      detail = body.detail ?? body.message ?? detail;
    } catch {
      // keep default detail
    }
    throw new Error(detail);
  }

  return res.json() as Promise<MediaUploadDto>;
}

async function listPaymentsByStatus(status: string, token?: string): Promise<PaymentDto[]> {
  const size = 100;
  let pageNumber = 0;
  let totalPages = 1;
  const all: PaymentDto[] = [];

  while (pageNumber < totalPages && pageNumber < 50) {
    const query = buildQuery({
      status,
      page: pageNumber,
      size,
      sort: 'createdAt,desc',
    });

    const page = await apiFetch<Page<PaymentDto>>(`/payments${query}`, {
      headers: authHeaders(token),
    });

    all.push(...(page.content ?? []));
    totalPages = Math.max(page.totalPages ?? 1, 1);
    pageNumber += 1;
  }

  return all;
}

export async function getPendingPayments(token?: string): Promise<PaymentDto[]> {
  try {
    return await listPaymentsByStatus('PENDING', token);
  } catch {
    return [];
  }
}

export async function getApprovedPayments(token?: string): Promise<PaymentDto[]> {
  try {
    return await listPaymentsByStatus('APPROVED', token);
  } catch {
    return [];
  }
}

export async function getReviewQueuePayments(token?: string): Promise<PaymentDto[]> {
  try {
    const [pending, submitted, underReview] = await Promise.all([
      listPaymentsByStatus('PENDING', token),
      listPaymentsByStatus('SUBMITTED', token),
      listPaymentsByStatus('UNDER_REVIEW', token),
    ]);

    const merged = [...pending, ...submitted, ...underReview];
    const byId = new Map<string, PaymentDto>();
    for (const payment of merged) {
      byId.set(payment.id, payment);
    }

    return [...byId.values()].sort(
      (a, b) => new Date(String(b.createdAt)).getTime() - new Date(String(a.createdAt)).getTime()
    );
  } catch {
    return [];
  }
}

export async function approvePayment(paymentId: string, reviewerId: string, token?: string): Promise<void> {
  await apiFetch<void>(`/payments/${encodeURIComponent(paymentId)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ action: 'APPROVE', reviewerId }),
    headers: authHeaders(token),
  });
}

export async function rejectPayment(paymentId: string, reviewerId: string, token?: string): Promise<void> {
  await apiFetch<void>(`/payments/${encodeURIComponent(paymentId)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ action: 'REJECT', reviewerId }),
    headers: authHeaders(token),
  });
}

export async function getPaymentByOrder(orderId: string, token: string): Promise<PaymentDto | null> {
  try {
    return await apiFetch<PaymentDto>(`/payments/order/${encodeURIComponent(orderId)}`, {
      headers: authHeaders(token),
    });
  } catch {
    return null;
  }
}

export async function submitPaymentProof(paymentId: string, proofReference: string, token: string): Promise<PaymentDto> {
  return apiFetch<PaymentDto>(`/payments/${encodeURIComponent(paymentId)}/proof`, {
    method: 'PATCH',
    body: JSON.stringify({ proofReference }),
    headers: authHeaders(token),
  });
}

export async function createGatewayCheckoutSession(
  paymentId: string,
  token: string
): Promise<PaymentGatewayCheckoutDto> {
  return apiFetch<PaymentGatewayCheckoutDto>(`/payments/${encodeURIComponent(paymentId)}/gateway/checkout`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export type GatewaySimulationStatus = 'APPROVED' | 'FAILED';

export async function simulateGatewayPaymentStatus(
  paymentId: string,
  gatewayStatus: GatewaySimulationStatus,
  signature?: string
): Promise<void> {
  const headers: Record<string, string> = {};
  if (signature) {
    headers['X-Gateway-Signature'] = signature;
  }
  await apiFetch<void>('/payments/webhooks/gateway', {
    method: 'POST',
    body: JSON.stringify({
      paymentId,
      gatewayStatus,
    }),
    headers,
  });
}

// ─── Auth Types ───────────────────────────────────────────────────────────────

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accountMerged?: boolean;
  userId: string;
  email: string;
  role: 'ADMIN' | 'SELLER' | 'CUSTOMER' | 'SUPERVISOR' | 'ADMINISTRACION' | 'DESPACHADOR';
  fullName?: string;
  avatarUrl?: string;
  permissions: string[];
  vigencyStart?: string;
  vigencyEnd?: string;
}

export interface UserProfileDto {
  id: string;
  email: string;
  fullName: string;
  phone?: string | null;
  notificationChannelPreference?: 'AUTO' | 'WHATSAPP' | 'EMAIL' | 'BOTH';
  role: 'ADMIN' | 'SELLER' | 'CUSTOMER';
  active: boolean;
  avatarUrl?: string | null;
}

export interface ReviewDto {
  id: string;
  productId: string;
  userId: string;
  rating: number;
  title: string | null;
  comment: string | null;
  approved: boolean;
  createdAt: string;
}

export interface ReviewSummaryDto {
  productId: string;
  avgRating: number;
  count: number;
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

export async function loginUser(email: string, password: string): Promise<AuthTokenResponse> {
  return apiFetch<AuthTokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export async function registerUser(
  email: string,
  password: string,
  fullName: string
): Promise<AuthTokenResponse> {
  return apiFetch<AuthTokenResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, fullName }),
  });
}

export async function googleLogin(idToken: string): Promise<AuthTokenResponse> {
  return apiFetch<AuthTokenResponse>('/auth/google', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  });
}

export async function getAuthMe(token: string): Promise<{ id: string; email: string; role: string; fullName?: string; avatarUrl?: string }> {
  return apiFetch<{ id: string; email: string; role: string; fullName?: string; avatarUrl?: string }>('/auth/me/profile', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function uploadMyAvatar(file: File, token: string): Promise<{ avatarUrl: string }> {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API_BASE}/auth/me/avatar`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<{ avatarUrl: string }>;
}

export async function getMyProfile(token: string): Promise<UserProfileDto> {
  return apiFetch<UserProfileDto>('/auth/me/profile', {
    headers: authHeaders(token),
  });
}

export async function updateMyProfile(
  fullName: string,
  phone: string,
  notificationChannelPreference: 'AUTO' | 'WHATSAPP' | 'EMAIL' | 'BOTH',
  token: string
): Promise<UserProfileDto> {
  return apiFetch<UserProfileDto>('/auth/me/profile', {
    method: 'PATCH',
    body: JSON.stringify({ fullName, phone, notificationChannelPreference }),
    headers: authHeaders(token),
  });
}

export async function changeMyPassword(currentPassword: string, newPassword: string, token: string): Promise<void> {
  await apiFetch<void>('/auth/me/password', {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
    headers: authHeaders(token),
  });
}

export async function getMyAddresses(token: string): Promise<CustomerAddressDto[]> {
  return apiFetch<CustomerAddressDto[]>('/auth/me/addresses', {
    headers: authHeaders(token),
  });
}

export async function createMyAddress(data: CreateCustomerAddressRequest, token: string): Promise<CustomerAddressDto> {
  return apiFetch<CustomerAddressDto>('/auth/me/addresses', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function updateMyAddress(
  addressId: string,
  data: UpdateCustomerAddressRequest,
  token: string
): Promise<CustomerAddressDto> {
  return apiFetch<CustomerAddressDto>(`/auth/me/addresses/${encodeURIComponent(addressId)}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function deleteMyAddress(addressId: string, token: string): Promise<void> {
  await apiFetch<void>(`/auth/me/addresses/${encodeURIComponent(addressId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export async function setMyAddressAsDefault(addressId: string, token: string): Promise<CustomerAddressDto> {
  return apiFetch<CustomerAddressDto>(`/auth/me/addresses/${encodeURIComponent(addressId)}/default`, {
    method: 'PATCH',
    headers: authHeaders(token),
  });
}

export async function getLocationTree(): Promise<LocationRegionDto[]> {
  return apiFetch<LocationRegionDto[]>('/locations/tree');
}

export async function getLocationCities(regionId: number): Promise<LocationCityDto[]> {
  return apiFetch<LocationCityDto[]>(`/locations/regions/${regionId}/cities`);
}

export async function getLocationCommunes(cityId: number): Promise<LocationCommuneDto[]> {
  return apiFetch<LocationCommuneDto[]>(`/locations/cities/${cityId}/comunas`);
}

export async function searchLocationCommunes(params: {
  q: string;
  regionId?: number;
  cityId?: number;
  limit?: number;
}): Promise<LocationCommuneDto[]> {
  const query = buildQuery({
    q: params.q,
    ...(params.regionId !== undefined ? { regionId: params.regionId } : {}),
    ...(params.cityId !== undefined ? { cityId: params.cityId } : {}),
    ...(params.limit !== undefined ? { limit: params.limit } : {}),
  });
  return apiFetch<LocationCommuneDto[]>(`/locations/comunas/search${query}`);
}

export async function getMyOrders(token: string, page = 0, size = 20): Promise<Page<OrderDto>> {
  try {
    const query = buildQuery({ page, size, sort: 'createdAt,desc' });
    return await apiFetch<Page<OrderDto>>(`/orders/mine${query}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    return { content: [], totalElements: 0, totalPages: 0, size, number: page };
  }
}

export async function createOrder(data: CreateOrderRequest, token: string): Promise<OrderDto> {
  return apiFetch<OrderDto>('/orders', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getOrderById(orderId: string, token: string): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${encodeURIComponent(orderId)}`, {
    headers: authHeaders(token),
  });
}

export async function confirmOrderDelivery(orderId: string, token: string): Promise<OrderDto> {
  return apiFetch<OrderDto>(`/orders/${encodeURIComponent(orderId)}/confirm-delivery`, {
    method: 'PATCH',
    headers: authHeaders(token),
  });
}
export async function getAdminOrdersByCustomer(
  customerId: string,
  token: string,
  page = 0,
  size = 30
): Promise<Page<OrderDto>> {
  try {
    const query = buildQuery({ customerId, page, size, sort: 'createdAt,desc' });
    return await apiFetch<Page<OrderDto>>(`/orders${query}`, {
      headers: authHeaders(token),
    });
  } catch {
    return { content: [], totalElements: 0, totalPages: 0, size, number: page };
  }
}

export async function getAdminUsers(
  token: string,
  page = 0,
  size = 200,
  filter?: AdminUsersFilter
): Promise<Page<AdminUserDto>> {
  const query = buildQuery({
    page,
    size,
    sort: 'createdAt,desc',
    role: filter?.role,
    active: filter?.active,
  });
  return apiFetch<Page<AdminUserDto>>(`/users${query}`, {
    headers: authHeaders(token),
  });
}

export async function updateAdminUser(userId: string, data: UpdateAdminUserRequest, token: string): Promise<AdminUserDto> {
  return apiFetch<AdminUserDto>(`/users/${encodeURIComponent(userId)}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function deleteAdminUser(userId: string, token: string): Promise<void> {
  await apiFetch<void>(`/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export async function resetAdminUserPassword(userId: string, newPassword: string, token: string): Promise<void> {
  await apiFetch<void>(`/users/${encodeURIComponent(userId)}/password`, {
    method: 'PATCH',
    body: JSON.stringify({ newPassword }),
    headers: authHeaders(token),
  });
}

export async function getSystemSettings(token: string): Promise<SystemSettingsDto> {
  return apiFetch<SystemSettingsDto>('/system-settings', {
    headers: authHeaders(token),
  });
}

export async function updateSystemSettings(
  data: UpdateSystemSettingsRequest,
  token: string
): Promise<SystemSettingsDto> {
  return apiFetch<SystemSettingsDto>('/system-settings', {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function getPublicStoreSettings(): Promise<PublicStoreSettingsDto | null> {
  try {
    return await apiFetch<PublicStoreSettingsDto>('/system-settings/public');
  } catch {
    return null;
  }
}

export interface PublicShippingConfig {
  zones: ShippingZoneConfig[];
  couriers: CourierConfig[];
  paymentMode: ShippingPaymentMode;
}

const FALLBACK_SHIPPING_ZONES: ShippingZoneConfig[] = [
  { code: 'LOCAL', titleEs: 'Zona local', titleEn: 'Local zone',
    etaEs: '24-48 hs', etaEn: '24-48h',
    comunas: ['Los Andes', 'San Felipe', 'Calle Larga', 'Rinconada'],
    active: true, sortOrder: 1 },
  { code: 'REGIONAL', titleEs: 'V Región y RM', titleEn: 'Valparaíso Region and Metropolitan Region',
    etaEs: '2-4 días hábiles', etaEn: '2-4 business days',
    comunas: [], active: true, sortOrder: 2 },
  { code: 'NACIONAL', titleEs: 'Otras regiones', titleEn: 'Other Chilean regions',
    etaEs: '3-7 días hábiles', etaEn: '3-7 business days',
    comunas: [], active: true, sortOrder: 3 },
];

const FALLBACK_SHIPPING_COURIERS: CourierConfig[] = [
  { id: 'starken', name: 'Starken', logoUrl: null, active: true },
  { id: 'chilexpress', name: 'ChilExpress', logoUrl: null, active: true },
];

export async function getPublicShippingConfig(): Promise<PublicShippingConfig> {
  const settings = await getPublicStoreSettings();
  let zones: ShippingZoneConfig[] = FALLBACK_SHIPPING_ZONES;
  let couriers: CourierConfig[] = FALLBACK_SHIPPING_COURIERS;
  const paymentMode: ShippingPaymentMode = settings?.shippingPaymentMode ?? 'POR_PAGAR';

  if (settings?.shippingZonesJson) {
    try {
      const parsed = JSON.parse(settings.shippingZonesJson);
      if (Array.isArray(parsed)) {
        zones = parsed
          .map((z: Partial<ShippingZoneConfig>) => ({
            code: (z.code as ShippingZoneCode) ?? 'LOCAL',
            titleEs: z.titleEs ?? '',
            titleEn: z.titleEn ?? '',
            etaEs: z.etaEs ?? '',
            etaEn: z.etaEn ?? '',
            comunas: Array.isArray(z.comunas) ? z.comunas.filter((c): c is string => typeof c === 'string') : [],
            active: z.active !== false,
            sortOrder: typeof z.sortOrder === 'number' ? z.sortOrder : 0,
          }))
          .sort((a, b) => a.sortOrder - b.sortOrder);
      }
    } catch {
      // keep fallback
    }
  }
  if (settings?.shippingCouriersJson) {
    try {
      const parsed = JSON.parse(settings.shippingCouriersJson);
      if (Array.isArray(parsed)) {
        couriers = parsed
          .map((c: Partial<CourierConfig>) => ({
            id: c.id ?? '',
            name: c.name ?? '',
            logoUrl: c.logoUrl ?? null,
            active: c.active !== false,
          }))
          .filter((c) => c.id && c.name);
      }
    } catch {
      // keep fallback
    }
  }
  return { zones, couriers, paymentMode };
}

export async function getCustomerCredit(customerId: string, token: string): Promise<CustomerCreditDto | null> {
  try {
    return await apiFetch<CustomerCreditDto>(`/customers/${encodeURIComponent(customerId)}/credit`, {
      headers: authHeaders(token),
    });
  } catch {
    return null;
  }
}

export async function grantCustomerCredit(
  customerId: string,
  amount: number,
  reason: string,
  token: string
): Promise<CustomerCreditDto> {
  return apiFetch<CustomerCreditDto>(`/customers/${encodeURIComponent(customerId)}/credit/grant`, {
    method: 'POST',
    body: JSON.stringify({ amount, reason }),
    headers: authHeaders(token),
  });
}

export async function getCustomerCreditMovements(
  customerId: string,
  token: string,
  page = 0,
  size = 100
): Promise<Page<CreditMovementDto>> {
  try {
    const query = buildQuery({ page, size, sort: 'createdAt,desc' });
    return await apiFetch<Page<CreditMovementDto>>(
      `/customers/${encodeURIComponent(customerId)}/credit/movements${query}`,
      { headers: authHeaders(token) }
    );
  } catch {
    return { content: [], totalElements: 0, totalPages: 0, size, number: page };
  }
}

// ─── Review API ───────────────────────────────────────────────────────────────

export async function getCurrentCashRegister(token: string): Promise<CashRegisterDto> {
  return apiFetch<CashRegisterDto>('/caja/current', {
    headers: authHeaders(token),
  });
}

export async function openCashRegister(openingBalance: number, token: string): Promise<CashRegisterDto> {
  return apiFetch<CashRegisterDto>('/caja/open', {
    method: 'POST',
    body: JSON.stringify({ openingBalance }),
    headers: authHeaders(token),
  });
}

export async function closeCashRegister(
  closingBalance: number,
  token: string,
  notes?: string
): Promise<CashRegisterDto> {
  return apiFetch<CashRegisterDto>('/caja/close', {
    method: 'POST',
    body: JSON.stringify({ closingBalance, notes }),
    headers: authHeaders(token),
  });
}

export async function addCashMovement(
  type: 'IN' | 'OUT',
  amount: number,
  description: string,
  token: string
): Promise<CashMovementDto> {
  return apiFetch<CashMovementDto>('/caja/movements', {
    method: 'POST',
    body: JSON.stringify({ type, amount, description }),
    headers: authHeaders(token),
  });
}

export async function getCashRegisterHistory(
  token: string,
  filter?: CashRegisterHistoryFilter,
  adminScope = false
): Promise<Page<CashRegisterDto>> {
  const query = buildQuery({
    status: filter?.status,
    from: filter?.from,
    to: filter?.to,
    sellerId: filter?.sellerId,
    page: filter?.page ?? 0,
    size: filter?.size ?? 10,
    sort: 'openedAt,desc',
  });
  const path = adminScope ? '/admin/caja' : '/caja/history';
  return apiFetch<Page<CashRegisterDto>>(`${path}${query}`, {
    headers: authHeaders(token),
  });
}

export async function getDispatchHistory(
  token: string,
  filter?: {
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }
): Promise<Page<DispatchHistoryRowDto>> {
  const query = buildQuery({
    from: filter?.from,
    to: filter?.to,
    page: filter?.page ?? 0,
    size: filter?.size ?? 20,
  });
  return apiFetch<Page<DispatchHistoryRowDto>>(`/admin/despachos/history${query}`, {
    headers: authHeaders(token),
  });
}

export async function getProductReviews(productId: string): Promise<ReviewDto[]> {
  try {
    return await apiFetch<ReviewDto[]>(`/products/${encodeURIComponent(productId)}/reviews`);
  } catch {
    return [];
  }
}

export async function getReviewSummary(productId: string): Promise<ReviewSummaryDto | null> {
  try {
    return await apiFetch<ReviewSummaryDto>(
      `/products/${encodeURIComponent(productId)}/reviews/summary`
    );
  } catch {
    return null;
  }
}

export async function createReview(
  productId: string,
  token: string,
  data: { rating: number; title?: string; comment?: string }
): Promise<ReviewDto> {
  return apiFetch<ReviewDto>(`/products/${encodeURIComponent(productId)}/reviews`, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function deleteReview(reviewId: string, token: string): Promise<void> {
  await apiFetch<void>(`/reviews/${encodeURIComponent(reviewId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getMyReviews(token: string): Promise<ReviewDto[]> {
  try {
    return await apiFetch<ReviewDto[]>('/reviews/mine', {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    return [];
  }
}

export async function getAdminReviews(token: string, approved?: boolean): Promise<ReviewDto[]> {
  const query = approved !== undefined ? `?approved=${approved}` : '';
  return apiFetch<ReviewDto[]>(`/reviews${query}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function approveReview(reviewId: string, token: string): Promise<ReviewDto> {
  return apiFetch<ReviewDto>(`/reviews/${encodeURIComponent(reviewId)}/approve`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  });
}

// ─── Category Types ───────────────────────────────────────────────────────────

export interface CategoryDto {
  id: string;
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId: string | null;
  sortOrder: number;
  active: boolean;
  featured: boolean;
  imageUrl?: string;
}

export interface CategoryTreeNode extends CategoryDto {
  children: CategoryTreeNode[];
}

export interface CreateCategoryRequest {
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId?: string;
  sortOrder: number;
  imageUrl?: string;
}

// ─── Category API ─────────────────────────────────────────────────────────────

export async function getCategories(): Promise<CategoryDto[]> {
  try {
    return await apiFetch<CategoryDto[]>('/categories');
  } catch {
    return [];
  }
}

export async function getCategoryTree(): Promise<CategoryTreeNode[]> {
  try {
    return await apiFetch<CategoryTreeNode[]>('/categories/tree');
  } catch {
    return [];
  }
}

export async function getFeaturedCategories(): Promise<CategoryDto[]> {
  try {
    return await apiFetch<CategoryDto[]>('/categories/featured');
  } catch {
    return [];
  }
}

export async function createCategory(data: CreateCategoryRequest, token: string): Promise<CategoryDto> {
  return apiFetch<CategoryDto>('/categories', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function updateCategory(
  id: string,
  data: Partial<CreateCategoryRequest & { active: boolean; featured: boolean }>,
  token: string
): Promise<CategoryDto> {
  return apiFetch<CategoryDto>(`/categories/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function deleteCategory(id: string, token: string): Promise<void> {
  await apiFetch<void>(`/categories/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function reorderCategories(
  items: { id: string; sortOrder: number }[],
  token: string
): Promise<void> {
  await apiFetch<void>('/categories/reorder', {
    method: 'PATCH',
    body: JSON.stringify({ items }),
    headers: authHeaders(token),
  });
}

// ─── Search API ───────────────────────────────────────────────────────────────

export interface ProductSearchParams {
  q?: string;
  active?: boolean;
  inStock?: boolean;
  condition?: 'NEW' | 'USED';
  category?: string;
  createdFrom?: string;
  createdTo?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export async function searchProducts(
  qOrParams: string | ProductSearchParams,
  page = 0,
  size = 12,
): Promise<Page<ProductDto>> {
  // Backwards-compat: accept legacy positional (q, page, size) signature for
  // storefront search overlay while admin uses the params-object form.
  const params: ProductSearchParams =
    typeof qOrParams === 'string'
      ? { q: qOrParams, active: true, inStock: true, page, size }
      : qOrParams;
  const trimmedQ = (params.q ?? '').trim();
  const queryString = buildQuery({
    ...(trimmedQ ? { q: trimmedQ } : {}),
    ...(params.active !== undefined ? { active: params.active } : {}),
    ...(params.inStock !== undefined ? { inStock: params.inStock } : {}),
    ...(params.condition ? { condition: params.condition } : {}),
    ...(params.category ? { category: params.category } : {}),
    ...(params.createdFrom ? { createdFrom: params.createdFrom } : {}),
    ...(params.createdTo ? { createdTo: params.createdTo } : {}),
    ...(params.sort ? { sort: params.sort } : {}),
    page: params.page ?? 0,
    size: params.size ?? 20,
  });
  try {
    const res = await apiFetch<Page<unknown>>(`/products/search${queryString}`);
    const normalized = res.content.map(normalizeProduct);
    const content = params.inStock ? normalized.filter(hasSellableStock) : normalized;
    return { ...res, content };
  } catch {
    const lower = trimmedQ.toLowerCase();
    const matches = FIXTURE_PRODUCTS.filter(p =>
      lower
        ? p.name.toLowerCase().includes(lower) || p.brand.toLowerCase().includes(lower)
        : true,
    );
    return { content: matches, totalElements: matches.length, totalPages: 1, size: matches.length, number: 0 };
  }
}

// ─── Wishlist API ─────────────────────────────────────────────────────────────

export async function getWishlist(token: string): Promise<WishlistDto> {
  return apiFetch<WishlistDto>('/wishlist', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function addToWishlist(productId: string, token: string): Promise<void> {
  await apiFetch<void>(`/wishlist/items/${encodeURIComponent(productId)}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function removeFromWishlist(productId: string, token: string): Promise<void> {
  await apiFetch<void>(`/wishlist/items/${encodeURIComponent(productId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getWishlistShareLink(token: string): Promise<WishlistShareLinkDto> {
  return apiFetch<WishlistShareLinkDto>('/wishlist/share-link', {
    headers: authHeaders(token),
  });
}

export async function enableWishlistShareLink(token: string): Promise<WishlistShareLinkDto> {
  return apiFetch<WishlistShareLinkDto>('/wishlist/share-link', {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export async function disableWishlistShareLink(token: string): Promise<void> {
  await apiFetch<void>('/wishlist/share-link', {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export async function getSharedWishlist(token: string): Promise<SharedWishlistDto> {
  return apiFetch<SharedWishlistDto>(`/wishlist/shared/${encodeURIComponent(token)}`);
}

// ─── Discount Codes ───────────────────────────────────────────────────────────

export interface DiscountCodeDto {
  id: string;
  code: string;
  type: string;
  value: number;
  minOrderAmount: number;
  minOrderCurrency: string;
  validFrom: string;
  validUntil: string;
  maxUses: number;
  timesUsed: number;
  active: boolean;
  assignedUserId?: string | null;
  assignedUserName?: string | null;
  assignedUserEmail?: string | null;
}

export interface CreateDiscountCodeRequest {
  code: string;
  type: string;
  value: number;
  minOrderAmount?: number;
  validFrom: string;
  validUntil: string;
  maxUses: number;
  assignedUserId?: string | null;
}

export async function listDiscountCodes(
  token: string,
  status: 'active' | 'expired' | 'all' = 'all'
): Promise<DiscountCodeDto[]> {
  return apiFetch<DiscountCodeDto[]>(`/discounts?status=${status}`, {
    headers: authHeaders(token),
  });
}

export async function createDiscountCode(
  data: CreateDiscountCodeRequest,
  token: string
): Promise<DiscountCodeDto> {
  return apiFetch<DiscountCodeDto>('/discounts', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function deleteDiscountCode(id: string, token: string): Promise<void> {
  await apiFetch<void>(`/discounts/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export async function suggestDiscountCode(token: string): Promise<string> {
  const res = await apiFetch<{ code: string }>('/discounts/suggest-code', {
    headers: authHeaders(token),
  });
  return res.code;
}

export async function validateDiscountCodeForUser(
  code: string,
  subtotal: number,
  token: string
): Promise<DiscountCodeDto> {
  return apiFetch<DiscountCodeDto>(
    `/discounts/validate-for-user?code=${encodeURIComponent(code)}&subtotal=${subtotal}`,
    { headers: authHeaders(token) }
  );
}

// ─── Notifications ────────────────────────────────────────────────────────────

export interface InAppNotificationDto {
  id: string;
  type: 'DISCOUNT_CODE_ASSIGNED' | 'ORDER_CONFIRMED' | 'PAYMENT_RECEIVED' | 'ORDER_PREPARING' | 'ORDER_SHIPPED';
  title: string;
  body: string;
  metadata: Record<string, unknown> | null;
  read: boolean;
  createdAt: string;
}

export interface UserSearchResultDto {
  id: string;
  fullName: string;
  email: string;
}

export async function getNotifications(
  token: string,
  page = 0,
  size = 20,
): Promise<Page<InAppNotificationDto>> {
  return apiFetch<Page<InAppNotificationDto>>(
    `/notifications?page=${page}&size=${size}`,
    { headers: authHeaders(token) },
  );
}

export async function getRecentNotifications(
  token: string,
  size = 5,
): Promise<Page<InAppNotificationDto>> {
  return apiFetch<Page<InAppNotificationDto>>(
    `/notifications?page=0&size=${size}&recentOnly=true`,
    { headers: authHeaders(token) },
  );
}

export async function getUnreadNotificationsCount(
  token: string,
): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/notifications/unread-count', {
    headers: authHeaders(token),
  });
}

export async function markNotificationRead(
  id: string,
  token: string,
): Promise<void> {
  return apiFetch<void>(`/notifications/${id}/read`, {
    method: 'PUT',
    headers: authHeaders(token),
  });
}

export async function markAllNotificationsRead(token: string): Promise<void> {
  return apiFetch<void>('/notifications/read-all', {
    method: 'PUT',
    headers: authHeaders(token),
  });
}

export async function searchUsers(
  query: string,
  token: string,
): Promise<UserSearchResultDto[]> {
  const q = encodeURIComponent(query);
  return apiFetch<UserSearchResultDto[]>(`/users/search?q=${q}`, {
    headers: authHeaders(token),
  });
}

export async function uploadMediaFile(file: File, folder: string, token: string): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  form.append('folder', folder);
  const res = await fetch(`${API_BASE}/media/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Upload failed (${res.status})`);
  }
  const data = await res.json() as MediaUploadDto;
  return data.url;
}

export async function createProductAiDraft(
  token: string,
  data?: Partial<{
    name: string;
    brand: string;
    condition: 'NEW' | 'USED';
    priceAmount: number;
    priceCurrency: string;
  }>
): Promise<ProductAiDraftDto> {
  return apiFetch<ProductAiDraftDto>('/admin/product-ai/drafts', {
    method: 'POST',
    body: JSON.stringify(data ?? {}),
    headers: authHeaders(token),
  });
}

export async function uploadProductAiDraftImages(
  draftId: string,
  files: File[],
  token: string,
  sourceFolder?: string,
): Promise<ProductAiUploadResultDto> {
  if (files.length === 0) {
    throw new Error('Debes seleccionar al menos una imagen');
  }
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  if (sourceFolder && sourceFolder.trim()) {
    form.append('sourceFolder', sourceFolder.trim());
  }

  const res = await fetch(`${API_BASE}/admin/product-ai/drafts/${encodeURIComponent(draftId)}/images`, {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as { detail?: string; message?: string };
    throw new Error(body.detail ?? body.message ?? `Upload failed (${res.status})`);
  }

  return res.json() as Promise<ProductAiUploadResultDto>;
}

export async function startProductAiJob(
  token: string,
  draftId: string,
  options?: Partial<{
    targetWidth: number;
    targetHeight: number;
    strictGarmentFidelity: boolean;
    forbidTextLogoWatermark: boolean;
  }>
): Promise<ProductAiJobDto> {
  return apiFetch<ProductAiJobDto>('/admin/product-ai/jobs', {
    method: 'POST',
    body: JSON.stringify({
      draftId,
      targetWidth: options?.targetWidth ?? 1024,
      targetHeight: options?.targetHeight ?? 1280,
      strictGarmentFidelity: options?.strictGarmentFidelity ?? true,
      forbidTextLogoWatermark: options?.forbidTextLogoWatermark ?? true,
    }),
    headers: authHeaders(token),
  });
}

export async function inferSingleProductAi(
  token: string,
  file: File,
  brandHint?: string,
): Promise<ProductAiInferenceDto> {
  const form = new FormData();
  form.append('file', file);
  if (brandHint && brandHint.trim()) {
    form.append('brandHint', brandHint.trim());
  }
  const res = await fetch(`${API_BASE}/admin/product-ai/infer-single`, {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });
  if (!res.ok) {
    if (res.status === 504) {
      throw new Error('La inferencia IA excedio el tiempo de espera del gateway. Reintenta en 15-30 segundos.');
    }
    const body = await res.json().catch(() => ({})) as { detail?: string; message?: string };
    throw new Error(body.detail ?? body.message ?? `Infer failed (${res.status})`);
  }
  return res.json() as Promise<ProductAiInferenceDto>;
}

export async function transformSingleProductAiImage(
  token: string,
  file: File,
  options?: Partial<{
    provider: 'OPENAI';
    prompt: string;
    brandHint: string;
  }>,
): Promise<ProductAiImageTransformDto> {
  const form = new FormData();
  form.append('file', file);
  if (options?.provider) {
    form.append('provider', options.provider);
  }
  if (options?.prompt && options.prompt.trim()) {
    form.append('prompt', options.prompt.trim());
  }
  if (options?.brandHint && options.brandHint.trim()) {
    form.append('brandHint', options.brandHint.trim());
  }
  const res = await fetch(`${API_BASE}/admin/product-ai/transform-single`, {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });
  if (!res.ok) {
    if (res.status === 504) {
      throw new Error('La transformacion IA excedio el tiempo de espera del gateway. Reintenta en 10-20 segundos.');
    }
    const body = await res.json().catch(() => ({})) as { detail?: string; message?: string };
    throw new Error(body.detail ?? body.message ?? `Transform failed (${res.status})`);
  }
  return res.json() as Promise<ProductAiImageTransformDto>;
}

export async function listProductAiJobs(token: string): Promise<ProductAiJobSummaryDto[]> {
  return apiFetch<ProductAiJobSummaryDto[]>('/admin/product-ai/jobs', {
    headers: authHeaders(token),
  });
}

export async function getProductAiJob(jobId: string, token: string): Promise<ProductAiJobDto> {
  return apiFetch<ProductAiJobDto>(`/admin/product-ai/jobs/${encodeURIComponent(jobId)}`, {
    headers: authHeaders(token),
  });
}

export async function retryProductAiJob(jobId: string, token: string): Promise<ProductAiJobDto> {
  return apiFetch<ProductAiJobDto>(`/admin/product-ai/jobs/${encodeURIComponent(jobId)}/retry`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export async function approvePublishProductAiDraft(
  draftId: string,
  token: string,
  payload?: Partial<{
    selectedAssetId: string;
    override: {
      name?: string;
      description?: string;
      brand?: string;
    };
  }>
): Promise<ProductAiPublishResultDto> {
  return apiFetch<ProductAiPublishResultDto>(`/admin/product-ai/drafts/${encodeURIComponent(draftId)}/approve-publish`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {}),
    headers: authHeaders(token),
  });
}

export async function migrateCategoryImages(token: string): Promise<{
  migrated: number;
  failed: number;
  errors: { categoryId: string; originalUrl: string; reason: string }[];
}> {
  return apiFetch('/admin/media/migrate-category-images', {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export interface OptimizeFolderResult {
  processed: number;
  renamed: number;
  skipped: number;
  failed: number;
  bytesSaved: number;
  errors: string[];
}

export interface OptimizeOthersResult {
  processed: number;
  skipped: number;
  failed: number;
  bytesSaved: number;
}

export interface OptimizeAllResult {
  products: OptimizeFolderResult;
  categories: OptimizeFolderResult;
  others: OptimizeOthersResult;
}

export interface ResizeProductsCategoriesResult {
  processed: number;
  resized: number;
  skipped: number;
  failed: number;
  targetLongSidePx: number;
}

export async function optimizeAllMedia(token: string): Promise<OptimizeAllResult> {
  return apiFetch<OptimizeAllResult>('/admin/media/optimize-all', {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export async function resizeProductsCategoriesTo15cm(token: string): Promise<ResizeProductsCategoriesResult> {
  return apiFetch<ResizeProductsCategoriesResult>('/admin/media/resize-products-categories-15cm', {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export type HeroModelSlot = 'left' | 'right';

export interface HeroModelSlotDto {
  slot: HeroModelSlot;
  url: string;
  updatedAt: number;
}

export interface HeroModelsDto {
  left: HeroModelSlotDto;
  right: HeroModelSlotDto;
}

export async function getHeroModels(token: string): Promise<HeroModelsDto> {
  return apiFetch<HeroModelsDto>('/admin/media/hero-models', {
    headers: authHeaders(token),
  });
}

export async function uploadHeroModel(slot: HeroModelSlot, file: File, token: string): Promise<HeroModelSlotDto> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${API_BASE}/admin/media/hero-models/${encodeURIComponent(slot)}`, {
    method: 'POST',
    headers: authHeaders(token),
    body: formData,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as { detail?: string; message?: string };
    throw new Error(body.detail ?? body.message ?? `Error al subir modelo (${res.status})`);
  }
  return res.json() as Promise<HeroModelSlotDto>;
}

export async function assignHeroModelFromProduct(
  slot: HeroModelSlot,
  productId: string,
  token: string,
): Promise<HeroModelSlotDto> {
  return apiFetch<HeroModelSlotDto>(`/admin/media/hero-models/${encodeURIComponent(slot)}/assign`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ productId }),
  });
}
