# Product Service

Read-oriented extracted service for catalog products (P6 step 1).

## Endpoints

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search?q=...`

## Local run

```bash
mvn spring-boot:run
```

Uses the same PostgreSQL schema as the monolith (`products`, `product_categories`, `product_size_stocks`, `product_variants`).
