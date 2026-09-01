# POS Channel — Future Integration

## Status

**NOT IMPLEMENTED.** The endpoint exists as a stub returning 501.

## Planned Contract

### POST /api/pos/sales

**Auth:** Bearer JWT, role SELLER or ADMIN

**Request body:**
```json
{
  "items": [
    { "productId": "uuid", "quantity": 1 }
  ],
  "paymentMethod": "CASH",
  "cashRegisterId": "uuid (optional)",
  "customerId": "uuid (optional, null = walk-in)"
}
```

**Behavior:**
- `salesChannel` is forced to `POS` (not taken from request)
- Creates an order via `CreateOrderUseCase` with `salesChannel=POS`
- If `paymentMethod == CASH`, calls `RegisterPosSaleUseCase` to record movement in the open cash register
- If `paymentMethod != CASH` (DEBIT, CREDIT, WEBPAY), no cash register movement is created
- Returns the created order

**Error responses:**
- `400` — invalid items, unknown paymentMethod
- `404` — product not found
- `409` — no open cash register (when paymentMethod=CASH)
- `501` — endpoint not yet implemented (current state)

## Caddy Routing Note

The microservices profile routes `POST /api/*` to the monolith backend (`backend:8080`).
`/api/pos/sales` is intentionally handled by the monolith only — do NOT route it to any extracted microservice.
If a `pos-service` is ever extracted, update `infra/Caddyfile` accordingly.

## Implementation Plan

The channel-agnostic engine now exists — `RegisterExternalSaleUseCase` +
`POST /api/admin/sales/external` (Fase 2 F, V94). It creates a born-PAID `Order` for a sale made
off-platform, with no registered customer, a free-text buyer, `SHIPPING`/`PICKUP` delivery, and
stock sold via reserve+confirm. **The POS endpoint is now a thin wrapper over it**, not a
`CreateOrderUseCase` caller:

1. Inject `RegisterExternalSaleUseCase` and `RegisterPosSaleUseCase` into `PosController`
2. Build a `RegisterExternalSaleCommand` with `salesChannel = SalesChannel.POS` (add `POS` to the
   controller's accepted-channel set, or give the POS controller its own command mapping) and the
   POS request's items/payment
3. `execute(...)`, then if `paymentMethod == CASH` call `RegisterPosSaleUseCase.execute(order.id,
   SalesChannel.POS, PaymentMethod.CASH)` for the cash movement; handle its `IllegalStateException`
   (no open register) as 409
4. Remove the `NOT_IMPLEMENTED` stub

Note: `SalesChannel` already has a `POS` value; `ExternalSaleController` currently restricts the
accepted channels to `{INSTAGRAM, FACEBOOK, WHATSAPP, MANUAL}` — POS goes through its own path.
