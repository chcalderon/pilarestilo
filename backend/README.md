# Pilar Estilo - Backend

Spring Boot 3.3 API for Pilar Estilo.

## Stack

- Java 17
- Spring Boot 3.3
- Spring Security + JWT
- Spring Data JPA + Hibernate
- Flyway
- PostgreSQL 16

---

## Architecture

Hexagonal (Ports and Adapters), module-oriented.

```
<module>/
  domain/
  application/
  infrastructure/
```

Rule: `domain/` remains framework-agnostic (no Spring/JPA annotations).

---

## Modules

| Module | Responsibility |
|---|---|
| `shared/auth` | JWT issuance/validation, auth filter, login/register/refresh/me |
| `product` | Product CRUD, catalog filters, search, rating summary fields, sizeStocks projection |
| `category` | Category tree and taxonomy |
| `review` | Product reviews + moderation workflow |
| `order` | Order aggregate and status transitions |
| `payment` | Payment registration, proof submission, review (approve/reject) |
| `discount` | Promo code creation/validation/application |
| `inventory` | Stock reservation/release and stock events |
| `wishlist` | Customer favorites (`/api/wishlist`) |
| `customercredit` | Credit balance and movement history |
| `notification` | Notification port + log adapter + domain listeners |
| `user` | User repository and user-facing data |

---

## Key API Surface

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`

### Catalog

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search?q=...`
- `GET /api/categories`
- `GET /api/categories/tree`
- `GET /api/media/**` (static media served from backend storage path)

### Wishlist

- `GET /api/wishlist`
- `POST /api/wishlist/items/{productId}`
- `DELETE /api/wishlist/items/{productId}`

### Payments

- `POST /api/payments`
- `PATCH /api/payments/{id}/proof`
- `PATCH /api/payments/{id}/review` (actions: `APPROVE` or `REJECT`)

---

## Running locally

### With Docker Compose (recommended)

```bash
cd infra
cp .env.example .env
docker compose up -d postgres
cd ../backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Full local stack

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
```

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `JWT_SECRET` | Yes | HS256 secret (min 32 bytes recommended) |
| `MEDIA_STORAGE_PATH` | No | Filesystem directory used by `/api/media/**` (default `./media`) |
| `SPRING_PROFILES_ACTIVE` | No | `local` for dev profile |
| `SERVER_PORT` | No | API port (default 8080) |

---

## Tests

```bash
mvn test      # unit tests
mvn verify    # includes integration tests (Testcontainers)
```

---

## Database migrations

Flyway scripts in `src/main/resources/db/migration` currently run from `V1` to `V11`, including:

- search indexes (`V7`)
- per-size stock schema (`V8`)
- wishlist schema (`V9`)
- Chile currency/default normalization (`V10`)
- product image path migration to backend media routes (`V11`)

---

## Seed admin (dev)

| Email | Password |
|---|---|
| `admin@pilarestilo.com` | `admin2026` |
