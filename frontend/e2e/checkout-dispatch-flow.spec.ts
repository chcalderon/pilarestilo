import { expect, test, type APIRequestContext } from '@playwright/test';

const ENV_BACKEND_API_BASE = process.env.INTERNAL_API_BASE_URL?.trim();
const BACKEND_API_CANDIDATES = [
  'http://127.0.0.1:8080/api',
  'http://localhost/api',
];
let backendApiBase = ENV_BACKEND_API_BASE || BACKEND_API_CANDIDATES[0];
const ADMIN_EMAIL = 'admin@pilarestilo.com';
const ADMIN_PASSWORD = 'admin2026';

type CheckoutPaymentMethod = 'TRANSFER' | 'WEBPAY';

interface AuthResponse {
  accessToken: string;
  userId: string;
  email: string;
  role: string;
  permissions: string[];
}

interface ProductPageResponse {
  content: Array<{
    id: string;
    name: string;
    brand: string;
    price: { amount: number; currency: string };
    imageUrl: string;
    condition: 'NEW' | 'USED';
    stock: number;
    variants?: Array<{ color?: string; size?: string; stock: number; stockAvailable?: number }>;
  }>;
}

interface OrderResponse {
  id: string;
  status: string;
  paymentMethod: CheckoutPaymentMethod;
  shippingZoneCode?: string | null;
  shippingCourierId?: string | null;
  shippingCourierName?: string | null;
  shippingPaymentMode?: string | null;
  shippingAddressId?: string | null;
  shippingAddressReference?: string | null;
}

interface AddressResponse {
  id: string;
  customerId: string;
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string | null;
  comuna: string;
  city: string;
  region: string;
  reference?: string | null;
  isDefault: boolean;
}

interface PaymentResponse {
  id: string;
  orderId: string;
  status: string;
}

interface DispatchRow {
  id: string;
  orderId: string;
  status: string;
  orderShippingZoneCode?: string | null;
  orderShippingCourierId?: string | null;
  orderShippingCourierName?: string | null;
  orderShippingAddressReference?: string | null;
}

interface PublicStoreSettingsResponse {
  paymentMethodBankTransferEnabled: boolean;
  paymentMethodGatewayEnabled: boolean;
  paymentGatewayProviders?: string[] | null;
  shippingZonesJson?: string | null;
  shippingCouriersJson?: string | null;
}

test.describe('Checkout -> dispatch -> customer delivery confirmation', () => {
  test('shipping method is preserved for TRANSFER and WEBPAY', async ({ request }) => {
    test.setTimeout(180000);
    backendApiBase = await resolveBackendApiBase(request);
    const backendAvailable = await isBackendAvailable(request, backendApiBase);
    test.skip(!backendAvailable, `Backend unavailable at ${backendApiBase}`);

    const admin = await login(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    const storeSettings = await getPublicStoreSettings(request);
    const shipping = resolveShippingSelection(storeSettings);

    for (const paymentMethod of ['TRANSFER', 'WEBPAY'] as const) {
      await test.step(`flow ${paymentMethod}`, async () => {
        test.skip(!isPaymentMethodAvailable(storeSettings, paymentMethod), `${paymentMethod} disabled in settings`);

        const ts = Date.now();
        const customerEmail = `flow_${paymentMethod.toLowerCase()}_${ts}@test.com`;
        const customerPassword = 'password123';
        const customerName = `Flow ${paymentMethod}`;

        await register(request, customerEmail, customerPassword, customerName);
        const customer = await login(request, customerEmail, customerPassword);

        const product = await pickSimpleInStockProduct(request);
        const address = await createAddress(request, customer.accessToken, ts, paymentMethod);
        const order = await createOrder(
          request,
          customer.accessToken,
          customer.userId,
          product,
          paymentMethod,
          shipping.zoneCode,
          shipping.courierId,
          address.id,
        );
        expect(order.paymentMethod).toBe(paymentMethod);
        const storedOrder = await apiJson<OrderResponse>(request, 'GET', `/orders/${order.id}`, {
          token: customer.accessToken,
        });
        expect(storedOrder.shippingZoneCode).toBe(shipping.zoneCode);
        expect(storedOrder.shippingCourierId).toBe(shipping.courierId);
        expect(storedOrder.shippingAddressId ?? null).toBe(address.id);
        expect(storedOrder.shippingAddressReference ?? '').toContain(address.line1);
        expect(storedOrder.shippingPaymentMode).toBe('POR_PAGAR');

        await settlePayment(request, {
          orderId: order.id,
          paymentMethod,
          adminToken: admin.accessToken,
          adminUserId: admin.userId,
          customerToken: customer.accessToken,
        });

        // With APP_DOMAIN_EVENTS_KAFKA_ENABLED the PaymentConfirmed -> order PAID transition
        // travels through Kafka, so it lands after settlePayment returns. Claiming the dispatch
        // before it does fails with "Cannot transition from CREATED, expected PAID".
        await waitForOrderStatus(request, customer.accessToken, order.id, 'PAID');

        const dispatch = await pollDispatchByOrder(request, admin.accessToken, order.id);
        if (dispatch.orderShippingZoneCode != null) {
          expect(dispatch.orderShippingZoneCode).toBe(shipping.zoneCode);
        }
        if (dispatch.orderShippingCourierId != null) {
          expect(dispatch.orderShippingCourierId).toBe(shipping.courierId);
        }
        if (dispatch.orderShippingAddressReference != null) {
          expect(dispatch.orderShippingAddressReference).toContain(address.line1);
        }

        // A paid order cannot be claimed for dispatch without a live boleta: the gate landed in
        // August and this spec predates it, so it was failing here on every run since. Registering
        // one is what the shop actually does between approving the transfer and packing the
        // parcel, so the flow is more truthful with it than it was without.
        await registerSalesDocument(request, admin.accessToken, order.id, `E2E-${paymentMethod}-${ts}`);

        await claimAndShipDispatch(request, admin.accessToken, dispatch, shipping.courierId, ts);
        await waitForOrderStatus(request, customer.accessToken, order.id, 'SHIPPED');

        await apiJson<OrderResponse>(request, 'PATCH', `/orders/${order.id}/confirm-delivery`, {
          token: customer.accessToken,
        });
        await waitForOrderStatus(request, customer.accessToken, order.id, 'DELIVERED');
      });
    }
  });
});

async function apiJson<T>(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PATCH',
  path: string,
  options?: { token?: string; body?: unknown },
): Promise<T> {
  const url = `${backendApiBase}${path}`;
  const headers: Record<string, string> = {};
  if (options?.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }
  let response;
  if (method === 'GET') {
    response = await request.get(url, { headers });
  } else if (method === 'POST') {
    response = await request.post(url, { headers, data: options?.body });
  } else {
    response = await request.patch(url, { headers, data: options?.body });
  }

  if (!response.ok()) {
    const text = await response.text();
    throw new Error(`${method} ${path} failed: ${response.status()} ${text}`);
  }
  if (response.status() === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text || !text.trim()) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

async function register(
  request: APIRequestContext,
  email: string,
  password: string,
  fullName: string,
): Promise<AuthResponse> {
  return apiJson<AuthResponse>(request, 'POST', '/auth/register', {
    body: { email, password, fullName },
  });
}

async function login(request: APIRequestContext, email: string, password: string): Promise<AuthResponse> {
  return apiJson<AuthResponse>(request, 'POST', '/auth/login', {
    body: { email, password },
  });
}

async function pickSimpleInStockProduct(
  request: APIRequestContext,
): Promise<{
  id: string;
  name: string;
  brand: string;
  price: { amount: number; currency: string };
  imageUrl: string;
  condition: 'NEW' | 'USED';
  variantColor?: string;
  variantSize?: string;
}> {
  const page = await apiJson<ProductPageResponse>(
    request,
    'GET',
    '/products?active=true&inStock=true&page=0&size=50',
  );
  for (const product of page.content) {
    if (Array.isArray(product.variants) && product.variants.length > 0) {
      // `stock` is a backward-compatible alias for stockOnHand and ignores reservations, so a
      // variant whose only unit is already reserved still looked available and the order failed
      // with "Inventory reservation rejected". stockAvailable is on-hand minus reserved.
      const variant = product.variants.find(
        (v) => Number(v.stockAvailable ?? v.stock ?? 0) > 0 && !!v.color && !!v.size,
      );
      if (variant) {
        return {
          ...product,
          variantColor: variant.color,
          variantSize: variant.size,
        };
      }
      continue;
    }
    if (Number(product.stock ?? 0) > 0) {
      return product;
    }
  }
  throw new Error('No in-stock product available for checkout flow test.');
}

async function createOrder(
  request: APIRequestContext,
  customerToken: string,
  customerId: string,
  product: {
    id: string;
    variantColor?: string;
    variantSize?: string;
  },
  paymentMethod: CheckoutPaymentMethod,
  shippingZoneCode: string,
  shippingCourierId: string,
  shippingAddressId: string,
): Promise<OrderResponse> {
  return apiJson<OrderResponse>(request, 'POST', '/orders', {
    token: customerToken,
    body: {
      customerId,
      items: [
        {
          productId: product.id,
          quantity: 1,
          ...(product.variantColor ? { variantColor: product.variantColor } : {}),
          ...(product.variantSize ? { variantSize: product.variantSize } : {}),
        },
      ],
      paymentMethod,
      shippingZoneCode,
      shippingCourierId,
      shippingAddressId,
    },
  });
}

type LocationCommune = { id: number; name: string };
type LocationCity = { id: number; name: string; communes: LocationCommune[] };
type LocationRegion = { id: number; name: string; cities: LocationCity[] };

/**
 * CreateCustomerAddressRequest requires regionId/cityId/comunaId alongside the display names.
 * Resolved from the catalog rather than hard-coded so the ids and the names stay consistent
 * and the test survives reseeded location data.
 */
async function firstCatalogLocation(request: APIRequestContext) {
  const tree = await apiJson<LocationRegion[]>(request, 'GET', '/locations/tree');
  const region = tree.find((r) => r.cities?.some((c) => c.communes?.length));
  const city = region?.cities.find((c) => c.communes?.length);
  const commune = city?.communes[0];
  if (!region || !city || !commune) {
    throw new Error('Location catalog has no region/city/comuna to build an address from');
  }
  return { region, city, commune };
}

async function createAddress(
  request: APIRequestContext,
  customerToken: string,
  seed: number,
  paymentMethod: CheckoutPaymentMethod,
): Promise<AddressResponse> {
  const { region, city, commune } = await firstCatalogLocation(request);
  return apiJson<AddressResponse>(request, 'POST', '/auth/me/addresses', {
    token: customerToken,
    body: {
      label: `Casa ${paymentMethod}`,
      recipientName: `Cliente ${paymentMethod}`,
      phone: '+56912345678',
      line1: `PW Calle ${seed}`,
      line2: 'Depto 10',
      regionId: region.id,
      cityId: city.id,
      comunaId: commune.id,
      comuna: commune.name,
      city: city.name,
      region: region.name,
      reference: 'Porteria',
      isDefault: true,
    },
  });
}

async function settlePayment(
  request: APIRequestContext,
  args: {
    orderId: string;
    paymentMethod: CheckoutPaymentMethod;
    adminToken: string;
    adminUserId: string;
    customerToken: string;
  },
): Promise<void> {
  let payment: PaymentResponse;
  try {
    payment = await apiJson<PaymentResponse>(
      request,
      'GET',
      `/payments/order/${encodeURIComponent(args.orderId)}`,
      { token: args.customerToken },
    );
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    if (!text.includes('404')) {
      throw error;
    }
    payment = await apiJson<PaymentResponse>(request, 'POST', '/payments', {
      token: args.adminToken,
      body: {
        orderId: args.orderId,
        paymentMethod: args.paymentMethod,
      },
    });
  }

  if (args.paymentMethod === 'TRANSFER') {
    await apiJson<PaymentResponse>(request, 'PATCH', `/payments/${payment.id}/proof`, {
      token: args.customerToken,
      body: { proofReference: 'https://example.com/proof.png' },
    });
    await apiJson<PaymentResponse>(request, 'PATCH', `/payments/${payment.id}/review`, {
      token: args.adminToken,
      body: { action: 'APPROVE', reviewerId: args.adminUserId },
    });
    return;
  }

  await apiJson(request, 'POST', `/payments/${payment.id}/gateway/checkout`, {
    token: args.customerToken,
  });
  await apiJson(request, 'POST', '/payments/webhooks/gateway', {
    body: {
      paymentId: payment.id,
      gatewayStatus: 'APPROVED',
    },
  });
}

async function pollDispatchByOrder(
  request: APIRequestContext,
  adminToken: string,
  orderId: string,
): Promise<DispatchRow> {
  const started = Date.now();
  while (Date.now() - started < 30000) {
    const rows = await apiJson<DispatchRow[]>(request, 'GET', '/despachos', { token: adminToken });
    const match = rows.find((row) => row.orderId === orderId);
    if (match) {
      return match;
    }
    await sleep(1000);
  }
  const seeded = await apiJson<DispatchRow>(request, 'POST', '/admin/despachos/seed', {
    token: adminToken,
    body: { orderId },
  });
  return seeded;
}

/**
 * Registers the boleta the dispatch gate asks for.
 *
 * <p>The folio is invented because in real life it comes from the SII's eBoleta app by hand; what
 * the gate checks is that a live document exists for the order, not what is printed on it.
 */
async function registerSalesDocument(
  request: APIRequestContext,
  adminToken: string,
  orderId: string,
  folio: string,
): Promise<void> {
  await apiJson(request, 'POST', '/admin/sales-documents', {
    token: adminToken,
    body: { orderId, folio },
  });
}

async function claimAndShipDispatch(
  request: APIRequestContext,
  adminToken: string,
  dispatch: DispatchRow,
  courierId: string,
  seed: number,
): Promise<void> {
  await apiJson(request, 'POST', `/despachos/${dispatch.id}/claim`, { token: adminToken });
  await apiJson(request, 'POST', `/despachos/${dispatch.id}/dispatch`, {
    token: adminToken,
    body: {
      carrier: courierId,
      trackingCode: `PW-TRK-${seed}`,
    },
  });
}

async function waitForOrderStatus(
  request: APIRequestContext,
  customerToken: string,
  orderId: string,
  expected: string,
): Promise<void> {
  const started = Date.now();
  while (Date.now() - started < 30000) {
    const order = await apiJson<OrderResponse>(request, 'GET', `/orders/${orderId}`, { token: customerToken });
    if (order.status === expected) {
      return;
    }
    await sleep(1000);
  }
  throw new Error(`Order ${orderId} did not reach status ${expected} within timeout.`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function getPublicStoreSettings(request: APIRequestContext): Promise<PublicStoreSettingsResponse> {
  return apiJson<PublicStoreSettingsResponse>(request, 'GET', '/system-settings/public');
}

function resolveShippingSelection(settings: PublicStoreSettingsResponse): { zoneCode: string; courierId: string } {
  const zones = safeJsonArray<{ code?: string; active?: boolean }>(settings.shippingZonesJson);
  const couriers = safeJsonArray<{ id?: string; active?: boolean }>(settings.shippingCouriersJson);
  const activeZones = zones.filter((zone) => zone.active !== false && zone.code).map((zone) => String(zone.code));
  const activeCouriers = couriers.filter((courier) => courier.active !== false && courier.id).map((courier) => String(courier.id));
  if (!activeZones.length || !activeCouriers.length) {
    throw new Error('No active shipping zones/couriers available in public store settings.');
  }
  return {
    zoneCode: activeZones[activeZones.length - 1],
    courierId: activeCouriers[activeCouriers.length - 1],
  };
}

function isPaymentMethodAvailable(settings: PublicStoreSettingsResponse, paymentMethod: CheckoutPaymentMethod): boolean {
  if (paymentMethod === 'TRANSFER') {
    return settings.paymentMethodBankTransferEnabled !== false;
  }
  const providers = settings.paymentGatewayProviders ?? [];
  return settings.paymentMethodGatewayEnabled !== false && providers.length > 0;
}

function safeJsonArray<T>(value: string | null | undefined): T[] {
  if (!value || !value.trim()) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

async function resolveBackendApiBase(request: APIRequestContext): Promise<string> {
  if (ENV_BACKEND_API_BASE) {
    return ENV_BACKEND_API_BASE.replace(/\/+$/, '');
  }
  for (const candidate of BACKEND_API_CANDIDATES) {
    const normalized = candidate.replace(/\/+$/, '');
    if (await isBackendAvailable(request, normalized)) {
      return normalized;
    }
  }
  return BACKEND_API_CANDIDATES[0];
}

async function isBackendAvailable(request: APIRequestContext, apiBase: string): Promise<boolean> {
  try {
    const response = await request.get(`${apiBase}/products?page=0&size=1`);
    return response.ok();
  } catch {
    return false;
  }
}
