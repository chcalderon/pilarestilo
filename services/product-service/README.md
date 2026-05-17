# Product Service

Read-oriented extracted service for catalog products (P6 step 1).

## Endpoints

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search?q=...`
- `GET /api/products/search?...&createdFrom=YYYY-MM-DD&createdTo=YYYY-MM-DD&sort=createdAt,desc`

## Local run

```bash
mvn spring-boot:run
```

Uses the same PostgreSQL schema as the monolith (`products`, `product_categories`, `product_size_stocks`, `product_variants`).
Keep this service aligned with the monolith product read contract, including:

- variant stock fields: `stock`, `stockOnHand`, `stockReserved`, `stockAvailable`
- category text search across `slug`, `nameEs`, and `nameEn`
- product variants stored in `product_variants.stock_on_hand` and `product_variants.stock_reserved`
