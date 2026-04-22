# Order Service

Extracted service for order reads and commands (P6 step 4/6).

## Endpoints

- `GET /api/orders`
- `GET /api/orders/{id}`
- `GET /api/orders/_health`
- `POST /api/orders`
- `PATCH /api/orders/{id}/status`

## Notes

- This service is intended for backend-to-backend query delegation.
- Public auth/authorization for `/api/orders/**` is enforced directly in this service (JWT).
- Backend internal calls can use `X-Service-Token` when `APP_ORDER_INTERNAL_TOKEN` is configured.

## Local run

```bash
mvn spring-boot:run
```
