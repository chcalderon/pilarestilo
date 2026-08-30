/*
 * Load test — purchase → dispatch → delivery, 9 concurrent buyers + 1 admin.
 *
 * Drives the same flow as frontend/e2e/checkout-dispatch-flow.spec.ts (TRANSFER path).
 * All data is simulated and disposable — see scripts/loadtest/DATA-MAP.md.
 *
 *   docker run --rm --network <net> -e BASE_URL=http://backend:8080/api \
 *     -v "$PWD/scripts/loadtest":/lt grafana/k6 run /lt/purchase-flow.js \
 *     --summary-export=/lt/summary.json
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://backend:8080/api';
const BUYERS = Number(__ENV.BUYERS || 9);
const HOLD = __ENV.HOLD || '8m';
const ADMIN_DURATION = __ENV.ADMIN_DURATION || '9m30s';
const PASSWORD = 'LoadTest2026!';
const PROOF_URL = 'https://placehold.co/400x600/png';

const purchaseComplete = new Counter('lt_purchase_complete');
const purchaseFailed = new Counter('lt_purchase_failed');
const purchaseDuration = new Trend('lt_purchase_duration_ms', true);
const stepOrder = new Trend('lt_step_create_order_ms', true);
const stepPayVisible = new Trend('lt_step_payment_visible_ms', true);
const waitShipped = new Trend('lt_wait_shipped_ms', true);
const adminSweep = new Trend('lt_admin_sweep_ms', true);
const adminApproved = new Counter('lt_admin_payments_approved');
const adminDispatched = new Counter('lt_admin_dispatched');

export const options = {
  scenarios: {
    buyers: {
      executor: 'ramping-vus',
      exec: 'buyer',
      startVUs: 0,
      stages: [
        { duration: '1m', target: BUYERS },
        { duration: HOLD, target: BUYERS },
        { duration: '30s', target: 0 },
      ],
      gracefulStop: '60s',
    },
    admin: {
      executor: 'constant-vus',
      exec: 'admin',
      vus: 1,
      duration: ADMIN_DURATION,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{expected_response:true}': ['p(95)<2500'],
    lt_purchase_failed: ['count<10'],
  },
};

function jitter(a, b) {
  sleep(a + Math.random() * (b - a));
}

function j(res) {
  try {
    return res.json();
  } catch (_) {
    return null;
  }
}

// ---------------------------------------------------------------- setup

export function setup() {
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: 'admin@pilarestilo.com',
    password: 'admin2026',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) {
    throw new Error(`admin login failed: ${login.status} ${login.body}`);
  }
  const admin = j(login);

  const settings = j(http.get(`${BASE}/system-settings/public`));
  const zones = safeArr(settings.shippingZonesJson).filter((z) => z && z.code && z.active !== false);
  const couriers = safeArr(settings.shippingCouriersJson).filter((c) => c && c.id && c.active !== false);
  if (!zones.length || !couriers.length) {
    throw new Error('no active shipping zone/courier in public settings');
  }

  const tree = j(http.get(`${BASE}/locations/tree`));
  let loc = null;
  for (const r of tree || []) {
    const city = (r.cities || []).find((c) => (c.communes || []).length);
    if (city) {
      loc = { regionId: r.id, region: r.name, cityId: city.id, city: city.name,
              comunaId: city.communes[0].id, comuna: city.communes[0].name };
      break;
    }
  }
  if (!loc) throw new Error('no region/city/comuna in /locations/tree');

  const prodPage = j(http.get(`${BASE}/products?active=true&inStock=true&page=0&size=50`));
  const products = [];
  for (const p of (prodPage && prodPage.content) || []) {
    const vs = p.variants || [];
    if (vs.length) {
      const v = vs.find((x) => Number(x.stockAvailable != null ? x.stockAvailable : x.stock || 0) > 0 && x.color && x.size);
      if (v) products.push({ id: p.id, variantColor: v.color, variantSize: v.size });
    } else if (Number(p.stock || 0) > 0) {
      products.push({ id: p.id });
    }
  }
  if (!products.length) throw new Error('no in-stock product for checkout');

  return {
    adminToken: admin.accessToken,
    adminUserId: admin.userId,
    zoneCode: zones[zones.length - 1].code,
    courierId: couriers[couriers.length - 1].id,
    loc,
    products,
  };
}

function safeArr(s) {
  if (!s) return [];
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : [];
  } catch (_) {
    return [];
  }
}

// ---------------------------------------------------------------- buyer

export function buyer(data) {
  const t0 = Date.now();
  const tag = `${__VU}_${__ITER}_${Date.now()}`;
  const email = `load_${tag}@loadtest.local`;
  const H = { headers: { 'Content-Type': 'application/json' } };

  const reg = http.post(`${BASE}/auth/register`, JSON.stringify({
    email, password: PASSWORD, fullName: `LoadTest Buyer ${__VU}`,
  }), H);
  if (!check(reg, { 'register 2xx': (r) => r.status === 200 || r.status === 201 })) {
    purchaseFailed.add(1);
    return;
  }
  const acc = j(reg);
  const B = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${acc.accessToken}` } };
  jitter(0.3, 1.0);

  const product = data.products[(__VU + __ITER) % data.products.length];

  const addr = http.post(`${BASE}/auth/me/addresses`, JSON.stringify({
    label: 'Casa LT', recipientName: `Cliente LT ${__VU}`, phone: '+56912345678',
    line1: `LT Calle ${tag}`, line2: 'Depto 10',
    regionId: data.loc.regionId, cityId: data.loc.cityId, comunaId: data.loc.comunaId,
    comuna: data.loc.comuna, city: data.loc.city, region: data.loc.region,
    reference: 'Porteria', isDefault: true,
  }), B);
  if (!check(addr, { 'address 2xx': (r) => r.status < 300 })) {
    purchaseFailed.add(1);
    return;
  }
  const addressId = j(addr).id;
  jitter(0.4, 1.2);

  const orderBody = {
    customerId: acc.userId,
    items: [Object.assign({ productId: product.id, quantity: 1 },
      product.variantColor ? { variantColor: product.variantColor, variantSize: product.variantSize } : {})],
    paymentMethod: 'TRANSFER',
    shippingZoneCode: data.zoneCode,
    shippingCourierId: data.courierId,
    shippingAddressId: addressId,
  };
  const so = Date.now();
  const orderRes = http.post(`${BASE}/orders`, JSON.stringify(orderBody), B);
  stepOrder.add(Date.now() - so);
  if (!check(orderRes, { 'order 2xx': (r) => r.status < 300 })) {
    purchaseFailed.add(1);
    return;
  }
  const orderId = j(orderRes).id;
  jitter(0.3, 0.8);

  // payment row is registered async off OrderCreated (Kafka) — poll for it
  const sp = Date.now();
  let paymentId = null;
  for (let i = 0; i < 30 && !paymentId; i++) {
    const pr = http.get(`${BASE}/payments/order/${orderId}`, B);
    if (pr.status === 200) { paymentId = j(pr).id; break; }
    sleep(1);
  }
  stepPayVisible.add(Date.now() - sp);
  if (!paymentId) { purchaseFailed.add(1); return; }

  const proof = http.patch(`${BASE}/payments/${paymentId}/proof`,
    JSON.stringify({ proofReference: PROOF_URL }), B);
  if (!check(proof, { 'proof 2xx': (r) => r.status < 300 })) {
    purchaseFailed.add(1);
    return;
  }

  // wait for the admin to approve + issue boleta + dispatch
  const sw = Date.now();
  let status = null;
  for (let i = 0; i < 100; i++) {
    const o = http.get(`${BASE}/orders/${orderId}`, B);
    if (o.status === 200) {
      status = j(o).status;
      if (status === 'SHIPPED' || status === 'DELIVERED') break;
    }
    sleep(1.5);
  }
  waitShipped.add(Date.now() - sw);
  if (status !== 'SHIPPED' && status !== 'DELIVERED') { purchaseFailed.add(1); return; }

  if (status === 'SHIPPED') {
    http.patch(`${BASE}/orders/${orderId}/confirm-delivery`, null, B);
    for (let i = 0; i < 15; i++) {
      const o = http.get(`${BASE}/orders/${orderId}`, B);
      if (o.status === 200 && j(o).status === 'DELIVERED') { status = 'DELIVERED'; break; }
      sleep(1);
    }
  }

  if (status === 'DELIVERED') {
    purchaseComplete.add(1);
    purchaseDuration.add(Date.now() - t0);
  } else {
    purchaseFailed.add(1);
  }
  jitter(0.5, 2.0);
}

// ---------------------------------------------------------------- admin

export function admin(data) {
  const A = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.adminToken}` } };
  const s0 = Date.now();

  const pl = http.get(`${BASE}/payments?size=100`, A);
  if (pl.status === 200) {
    const content = (j(pl) || {}).content || [];
    for (const p of content) {
      if (p.status === 'SUBMITTED' || p.status === 'UNDER_REVIEW') {
        const rv = http.patch(`${BASE}/payments/${p.id}/review`,
          JSON.stringify({ action: 'APPROVE', reviewerId: data.adminUserId }), A);
        if (rv.status < 300) adminApproved.add(1);
      }
    }
  }

  const dl = http.get(`${BASE}/despachos`, A);
  if (dl.status === 200) {
    const rows = j(dl) || [];
    for (const d of rows) {
      const st = (d.status || '').toUpperCase();
      if (st === 'SHIPPED' || st === 'DELIVERED') continue;
      http.post(`${BASE}/admin/sales-documents`,
        JSON.stringify({ orderId: d.orderId, folio: `LT-${String(d.orderId).slice(0, 8)}` }), A);
      http.post(`${BASE}/despachos/${d.id}/claim`, null, A);
      const ds = http.post(`${BASE}/despachos/${d.id}/dispatch`,
        JSON.stringify({ carrier: data.courierId, trackingCode: `LT-TRK-${Date.now()}` }), A);
      if (ds.status < 300) adminDispatched.add(1);
    }
  }

  adminSweep.add(Date.now() - s0);
  sleep(1.5);
}
