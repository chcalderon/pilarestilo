# Pilar Estilo - Payment Flow

This document is aligned with the current backend implementation.

## 1. Current Approach

Payments are semi-manual and admin-reviewed. There is no active external gateway adapter yet.

Supported methods in domain enum:

- `BANK_TRANSFER`
- `CASH_ON_DELIVERY`
- `AGREED_BY_WHATSAPP`
- `STORE_CREDIT`
- `PAYMENT_GATEWAY` (reserved seam, not wired to provider yet)

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

---

## 6. Gateway Upgrade Path

`PaymentGatewayPort` is already present in domain. A future adapter (Mercado Pago/Stripe) can be added in infrastructure without changing domain models.

