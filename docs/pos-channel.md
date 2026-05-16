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

When ready to implement:
1. Inject `CreateOrderUseCase` and `RegisterPosSaleUseCase` into `PosController`
2. Map request body to `CreateOrderCommand` with `salesChannel = SalesChannel.POS`
3. Execute use case, handle `IllegalStateException` (no open register) as 409
4. Remove `throw new ResponseStatusException(NOT_IMPLEMENTED, ...)` stub
