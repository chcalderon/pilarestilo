# Pilar Estilo - Payment Flow

This document is aligned with the current backend implementation.

## 1. Current Approach

Payments currently support two operational paths:

- Semi-manual transfer flow (customer proof + admin review)
- Gateway-ready flow with checkout session + webhook processing (currently wired with stub adapter)

Supported methods in domain enum:

- `BANK_TRANSFER`
- `CASH_ON_DELIVERY`
- `AGREED_BY_WHATSAPP`
- `STORE_CREDIT`
- `PAYMENT_GATEWAY`

---

## 2. Payment Status Machine

```
PENDING -> SUBMITTED -> UNDER_REVIEW -> APPROVED
                               \------> REJECTED
```

Important implementation detail:

- `UNDER_REVIEW` is entered automatically inside review use cases when a payment is still `SUBMITTED`.
- There is no dedicated public endpoint for "mark under review only".

---

## 3. API Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/payments` | Register payment for an order |
| `GET` | `/api/payments/order/{orderId}` | Resolve payment linked to an order (customer ownership enforced) |
| `PATCH` | `/api/payments/{id}/proof` | Submit payment proof (`proofReference`) |
| `PATCH` | `/api/payments/{id}/review` | Review action (`APPROVE` or `REJECT`) |
| `POST` | `/api/payments/{id}/gateway/checkout` | Create checkout session for gateway payment |
| `POST` | `/api/payments/webhooks/gateway` | Receive gateway webhook event (public endpoint, optional signature) |
| `POST` | `/api/payments/webhooks/gateway/mercadopago` | Receive Mercado Pago webhook event (public endpoint, optional `token` query validation) |
| `GET` | `/api/payments/{id}` | Get payment detail |
| `GET` | `/api/payments?status=...` | List/filter payments |

Supporting media upload endpoint used by storefront proof flow:

- `POST /api/media/upload-proof` (authenticated)

`/review` request shape:

```json
{
  "action": "APPROVE",
  "reviewerId": "00000000-0000-0000-0000-000000000000"
}
```

---

## 4. Runtime Flow

### Step 1 - Order creation

- `CreateOrderUseCase` publishes `OrderCreated`.
- `OrderEventListener` auto-registers a `PENDING` payment.

### Step 2 - Customer submits proof

- Storefront account page resolves order payment with `GET /api/payments/order/{orderId}`.
- Customer can upload a proof image with `POST /api/media/upload-proof` or paste a manual URL.
- `PATCH /api/payments/{id}/proof`
- `SubmitPaymentProofUseCase` sets status to `SUBMITTED`.
- `PaymentSubmitted` event is published.

### Step 2b - Customer starts gateway checkout

- For orders created with `PAYMENT_GATEWAY`, authenticated user can call:
  - `POST /api/payments/{id}/gateway/checkout`
- Backend resolves order total and asks `PaymentGatewayPort` for checkout session data.
- Adapter selection is controlled by `PAYMENT_GATEWAY_PROVIDER`:
  - `STUB` (default): returns synthetic checkout sessions for local simulation (default redirect `/es/account?tab=orders&ref=...`).
  - `MERCADO_PAGO`: creates real preference sessions in Mercado Pago API.
- Checkout response always returns:
  - `gatewayReference`
  - `checkoutUrl`
  - `expiresAt`

### Step 2c - Temporary simulation mode (dev/staging)

- Storefront cart exposes payment method selector:
  - `Transferencia` -> regular proof-based flow
  - `Pasarela (simulada)` -> gateway webhook simulation flow
- In account orders (`PAYMENT_GATEWAY` only), users can trigger:
  - `Simular aprobado`
  - `Simular rechazado`
- In admin payment queue, pending gateway rows expose:
  - `Sim aprobar`
  - `Sim rechazar`
- These controls call the same webhook endpoint used by provider integrations:
  - `POST /api/payments/webhooks/gateway`

### Step 3 - Admin decision

- `PATCH /api/payments/{id}/review`
- `ReviewPaymentUseCase` transitions:
  - `SUBMITTED` -> `UNDER_REVIEW` -> `APPROVED`, or
  - `SUBMITTED` -> `UNDER_REVIEW` -> `REJECTED`
- Admin queue UI includes `PENDING` rows for visibility, but review actions are enabled only for `SUBMITTED` and `UNDER_REVIEW`.

### Step 4 - Post-review events

**On APPROVED:**
- `PaymentConfirmed` event published.
- `PaymentEventListener.onPaymentConfirmed` moves order to `PAID` (with idempotency guard - skips if order already past payment stage).
- Notification listener also reacts to `PaymentConfirmed` (currently log-based adapter).

**On REJECTED:**
- `PaymentRejected` event published.
- `PaymentEventListener.onPaymentRejected` moves order to `CANCELLED` and releases inventory for each line item.
- Idempotency guard: skips if order already in `CANCELLED`, `PAID`, `PREPARING_ORDER`, `SHIPPED`, or `DELIVERED`.

### Step 5 - Gateway webhook decisions

- Gateway posts to `POST /api/payments/webhooks/gateway` with:
  - `paymentId`
  - `gatewayStatus` (for example: `APPROVED`, `FAILED`, `PENDING`)
- If `PAYMENT_GATEWAY_WEBHOOK_SECRET` is configured, header `X-Gateway-Signature` must match.
- Webhook processor maps statuses:
  - Approved-like -> payment `APPROVED` + `PaymentConfirmed` event
  - Rejected-like -> payment `REJECTED` + `PaymentRejected` event
  - Pending-like -> accepted with no state transition
- Duplicate webhook deliveries are idempotent on final states.
- This endpoint is currently reused by temporary UI simulation controls for non-production environments.

### Step 5b - Mercado Pago webhook bridge

- Mercado Pago can call:
  - `POST /api/payments/webhooks/gateway/mercadopago`
- Endpoint resolves provider payment id (`id` query/body), loads payment status from Mercado Pago API, maps by `external_reference` (order id), and reuses the same gateway state machine.
- Optional security hardening:
  - Set `PAYMENT_GATEWAY_MP_WEBHOOK_TOKEN` and include it as `token` in notification URL.

---

## 5. API Examples

Get payment by order:

```bash
curl -X GET /api/payments/order/{orderId} \
  -H "Authorization: Bearer <token>"
```

Submit proof:

```bash
curl -X PATCH /api/payments/{id}/proof \
  -H "Content-Type: application/json" \
  -d '{"proofReference":"TXN-20260420-001"}'
```

Approve:

```bash
curl -X PATCH /api/payments/{id}/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"APPROVE","reviewerId":"<admin-uuid>"}'
```

Reject:

```bash
curl -X PATCH /api/payments/{id}/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"REJECT","reviewerId":"<admin-uuid>"}'
```

Simulate gateway approval (dev):

```bash
curl -X POST /api/payments/webhooks/gateway \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"<payment-uuid>","gatewayStatus":"APPROVED"}'
```

Mercado Pago webhook callback (provider-managed):

```bash
curl -X POST "/api/payments/webhooks/gateway/mercadopago?id=<mp-payment-id>&topic=payment&token=<optional-token>"
```

---

## 6. Gateway Upgrade Path

`PaymentGatewayPort` is active in domain and currently backed by `StubPaymentGatewayAdapter`.

Next step to reach production gateway:

- Replace stub with a provider adapter (Mercado Pago or Stripe)
- Keep current checkout and webhook endpoints as stable contracts

