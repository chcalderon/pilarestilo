# Inventory Service

Extracted inventory service for stock reads and stock command writes (P6 steps 2-3).

## Endpoints

- `GET /api/inventory/products`
- `GET /api/inventory/products/{id}`
- `GET /api/inventory/_health`
- `POST /api/inventory/commands/reserve`
- `POST /api/inventory/commands/release`
- `POST /api/inventory/commands/confirm`

Command payload:

```json
{
  "productId": "00000000-0000-0000-0000-000000000001",
  "qty": 1
}
```

## Local run

```bash
mvn spring-boot:run
```

Uses the same PostgreSQL schema as the monolith (`products`, `product_size_stocks`, `product_variants`, `product_categories`).
