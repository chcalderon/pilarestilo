# Pilar Estilo — Payment Flow

## 1. Philosophy

Pilar Estilo v1 uses a **semi-manual, admin-controlled payment flow**. There is no real-time payment gateway integration in v1. Instead, customers submit proof of payment (bank transfer reference, WhatsApp screenshot, etc.) and an admin reviews and approves or rejects it.

This design was chosen deliberately:

- The target market operates heavily on WhatsApp commerce and bank transfers. Automated gateway processing would add friction for the primary payment channel.
- It keeps v1 simple and deployable without payment gateway credentials or PCI compliance scope.
- The `PaymentGatewayPort` seam (see section 6) is already defined, so upgrading to automated processing is a backend adapter swap, not an architectural change.

---

## 2. Payment Methods

| Method | Enum Value | Description |
|---|---|---|
| Bank Transfer | `BANK_TRANSFER` | Customer initiates a bank transfer and submits the transaction reference or receipt as proof. Admin verifies against the bank account. Most common method. |
| Cash on Delivery | `CASH_ON_DELIVERY` | Driver or courier collects cash at delivery. Payment is registered as confirmed when the delivery is marked complete. |
| Agreed by WhatsApp | `AGREED_BY_WHATSAPP` | Informal arrangement confirmed via WhatsApp conversation. Customer typically shares a payment screenshot. Admin confirms manually. |
| Store Credit | `STORE_CREDIT` | Deducted from the customer's store credit balance (managed by the `customercredit` module). If the order total exceeds the credit balance, the remaining amount may be combined with another method. |
| Payment Gateway | `PAYMENT_GATEWAY` | **Stub — not active in v1.** Reserved for future Stripe / Mercado Pago integration. The `PaymentGatewayPort` interface is already defined; no gateway adapter is wired yet. |

---

## 3. Payment State Machine

```
                      ┌──────────┐
         Order        │          │
         Created  ──► │ PENDING  │
                      │          │
                      └────┬─────┘
                           │
                           │  Customer submits proof
                           │  POST /api/payments/{id}/proof
                           ▼
                      ┌──────────┐
                      │SUBMITTED │
                      └────┬─────┘
                           │
                           │  Admin marks for review
                           │  PATCH /api/payments/{id}/review
                           │  action=MARK_UNDER_REVIEW
                           ▼
                      ┌─────────────┐
                      │UNDER_REVIEW │
                      └──────┬──────┘
                             │
               ┌─────────────┴─────────────┐
               │ action=APPROVE            │ action=REJECT
               ▼                           ▼
         ┌──────────┐               ┌──────────┐
         │ APPROVED │               │ REJECTED │
         └──────────┘               └──────────┘
```

**Terminal states:** `APPROVED` and `REJECTED`. Once a payment reaches either state it cannot be transitioned further without creating a new payment record.

**Invalid transitions** return HTTP 409 Conflict with a descriptive error message.

---

## 4. Step-by-Step Flow

### Step 1 — Customer Creates Order

Customer submits `POST /api/orders` with line items, shipping address, and chosen `paymentMethod`.

- `CreateOrderUseCase` persists the `Order` with status `AWAITING_PAYMENT`.
- `OrderCreated` domain event is published.
- `OrderCreatedListener` in the `payment` module receives the event and automatically creates a `Payment` record with status `PENDING`, linked to the order.

### Step 2 — Customer Submits Payment Proof

Customer calls `POST /api/payments/{id}/proof` with the reference or proof details (bank reference number, screenshot URL, or any text description).

- `SubmitPaymentProofUseCase` validates the transition (`PENDING` → `SUBMITTED`).
- Payment status is updated to `SUBMITTED`.
- `PaymentSubmitted` event is published.
- Admin is notified (v1: logged; P2: WhatsApp/email notification via Twilio or SendGrid).

### Step 3 — Admin Marks for Review

Admin calls `PATCH /api/payments/{id}/review` with body `{ "action": "MARK_UNDER_REVIEW" }`.

- `ReviewPaymentUseCase` validates the transition (`SUBMITTED` → `UNDER_REVIEW`).
- Payment is assigned to the reviewing admin (stored as `reviewedBy`).
- Status updated to `UNDER_REVIEW`.

This step is optional but recommended for teams with multiple admins to avoid concurrent review conflicts.

### Step 4 — Admin Approves

Admin calls `PATCH /api/payments/{id}/review` with body `{ "action": "APPROVE" }`.

- `ReviewPaymentUseCase` validates the transition (`UNDER_REVIEW` → `APPROVED`).
- Payment status updated to `APPROVED`.
- `PaymentConfirmed` domain event is published.
- Subscribers act on `PaymentConfirmed`:
  - `order` module: marks order status as `PAID`.
  - `inventory` module: converts stock reservation to confirmed deduction.
  - `notification` module (P2): sends confirmation to customer via WhatsApp/email.

### Step 5 — Admin Rejects

Admin calls `PATCH /api/payments/{id}/review` with body `{ "action": "REJECT", "reason": "Reference not found in bank statement" }`.

- `ReviewPaymentUseCase` validates the transition (`UNDER_REVIEW` → `REJECTED`).
- Payment status updated to `REJECTED`.
- `PaymentRejected` domain event is published.
- Subscribers act on `PaymentRejected`:
  - `order` module: marks order status as `PAYMENT_FAILED`.
  - `inventory` module: releases stock reservation back to available.
  - `notification` module (P2): alerts customer with rejection reason.

---

## 5. Admin Workflow

### Payment Review Queue

The admin UI exposes a **Payment Review Queue** at `/admin/payments`. It lists all payments filtered by status. Typical admin session:

1. Open queue filtered to `SUBMITTED` — these need attention.
2. Click a payment to view proof details, order summary, and customer info.
3. Click **Mark Under Review** (sets `UNDER_REVIEW`, prevents another admin from acting on it simultaneously).
4. Verify proof against bank records or WhatsApp chat.
5. Click **Approve** or **Reject** (with optional rejection reason).

### Direct API Access

All transitions are available via REST for scripting or integration:

```bash
# Submit proof (customer action)
curl -X POST /api/payments/{id}/proof \
  -H "Content-Type: application/json" \
  -d '{"reference": "TXN-20260417-001234", "notes": "Transferred at 14:32"}'

# Mark under review (admin)
curl -X PATCH /api/payments/{id}/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"action": "MARK_UNDER_REVIEW"}'

# Approve (admin)
curl -X PATCH /api/payments/{id}/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"action": "APPROVE"}'

# Reject (admin)
curl -X PATCH /api/payments/{id}/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"action": "REJECT", "reason": "Bank reference not found"}'
```

---

## 6. Future Gateway Integration

### The PaymentGatewayPort Seam

The `payment` module defines a `PaymentGatewayPort` interface in its domain ports:

```java
// payment/domain/ports/PaymentGatewayPort.java
public interface PaymentGatewayPort {
    GatewayChargeResult charge(GatewayChargeRequest request);
    GatewayRefundResult refund(GatewayRefundRequest request);
    GatewayWebhookEvent parseWebhook(String payload, String signature);
}
```

In v1, no bean implements this interface. The `PAYMENT_GATEWAY` method is accepted by the API but falls through to the manual review flow.

### Implementing a Stripe Adapter

1. Add `stripe-java` dependency to `pom.xml`.
2. Create `StripePaymentGatewayAdapter implements PaymentGatewayPort` in `payment/infrastructure/gateway/stripe/`.
3. Annotate with `@Component @ConditionalOnProperty(name="payment.gateway", havingValue="stripe")`.
4. Implement `charge()` to call `PaymentIntent.create()` via the Stripe SDK.
5. Implement `parseWebhook()` to verify Stripe webhook signatures and map to `GatewayWebhookEvent`.
6. Register a webhook endpoint `POST /api/payments/webhook/stripe` that calls `parseWebhook()` and routes to the appropriate use case.

### PAYMENT_GATEWAY Flow With Gateway Active

When a gateway adapter is wired:

```
Customer checks out with PAYMENT_GATEWAY method
     │
     ▼
POST /api/orders  →  OrderCreated  →  Payment(PENDING) created
     │
     ▼
POST /api/payments/{id}/initiate-gateway
     │  calls PaymentGatewayPort.charge()
     │  returns clientSecret / redirect URL to frontend
     ▼
Customer completes payment on Stripe hosted page
     │
     ▼
Stripe webhook → POST /api/payments/webhook/stripe
     │  parseWebhook() → PaymentConfirmed or PaymentRejected
     ▼
Same downstream effects as manual APPROVED/REJECTED flow
```

This design means the order, inventory, and notification logic is **unchanged** regardless of whether payment is manual or gateway-automated — they both consume the same `PaymentConfirmed` / `PaymentRejected` domain events.
