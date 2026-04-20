# Pilar Estilo - Architecture

This file documents the architecture currently implemented in the repository.

## 1. Overview

Pilar Estilo is a monorepo ecommerce platform with:

- SSR storefront and admin UI
- REST backend with domain-first modules
- PostgreSQL persistence
- Docker Compose local/prod baseline
- Caddy reverse proxy for routing/TLS

Monorepo layout:

```text
PilarEstilo/
  backend/
  frontend/
  infra/
  docs/
```

---

## 2. Frontend

### Stack

- Astro 4 (SSR + i18n routes)
- React islands for interaction
- Tailwind with brand tokens
- Zustand for auth, cart, and wishlist state

### Route model

- Storefront: `/es/*` and `/en/*`
- Admin: `/admin/*` (no locale prefix)

### Notable implemented features

- JWT-aware SSR middleware guard for admin routes
- Search overlay wired to `/api/products/search`
- Wishlist page and heart actions
- Product detail size selector consuming `sizeStocks`
- Account self-service profile/password management
- Admin user management surface (`/admin/users`) with role/status/password/credit actions
- Payment-gateway simulation controls in account/admin workflows for non-production environments

---

## 3. Backend

### Stack

- Java 17 + Spring Boot 3.3
- Spring Security + JWT filter
- Spring Data JPA + Hibernate
- Flyway migrations
- PostgreSQL 16

### Architecture style

Hexagonal (Ports and Adapters):

- `domain/` for pure business model and ports
- `application/` for use-case orchestration
- `infrastructure/` for web, persistence, listeners, adapters

Rule: no Spring/JPA annotations inside `domain/`.

### Modules in codebase

- `product`
- `category`
- `review`
- `order`
- `payment`
- `discount`
- `inventory`
- `user`
- `customercredit`
- `wishlist`
- `notification`
- `shared` (`auth`, `domain`, common infra)

### Media delivery

- Backend serves static product media via `/api/media/**`.
- `MediaResourceConfig` maps that route to `app.media.storage-path` (filesystem).
- Docker Compose binds that path to `infra/storage/media` for persistence.

---

## 4. Eventing Model

The project uses a `DomainEventPublisher` port with an in-process Spring implementation by default.

Current listeners include:

- `OrderCreated` -> payment registration
- `PaymentConfirmed` -> order moved to `PAID`
- Review events -> product rating denormalization
- Notification listeners for order/payment confirmation hooks

The Kafka migration seam remains available via publisher adapter swap.

---

## 5. Data and Migrations

Flyway migrations currently include baseline plus catalog refinements:

- `V1` to `V6`: core schema + auth/categories/reviews + seed updates
- `V7`: search indexes (`pg_trgm`)
- `V8`: per-size stock table + shipping origin
- `V9`: wishlist schema
- `V10`: Chile defaults (`CLP`, shipping zone normalization, category associations)
- `V11`: seeded product image URLs moved to backend media routes (`/api/media/products/*.jpg`)
- `V12`: user active-flag support for account blocking/unblocking workflows

---

## 6. Runtime Topology (Docker Compose)

`infra/docker-compose.yml` defines:

- `postgres`
- `backend`
- `frontend`
- `caddy`

Caddy routes `/api/*` to backend and all other routes to frontend.
