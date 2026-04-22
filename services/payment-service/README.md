# Payment Service

Extracted query-oriented service for payment reads (P6 step 5).

## Endpoints

- `GET /api/payments`
- `GET /api/payments/{id}`
- `GET /api/payments/order/{orderId}`
- `GET /api/payments/_health`

## Notes

- This service now enforces JWT auth/role rules for payment query endpoints.
- `CUSTOMER` users can only access `/api/payments/order/{orderId}` for their own orders.
- Trusted backend-to-payment-service calls can use `X-Service-Token` when
  `APP_PAYMENT_INTERNAL_TOKEN` is configured.

## Local run

```bash
mvn spring-boot:run
```
