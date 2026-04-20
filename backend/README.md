# Pilar Estilo — Backend

Spring Boot 3.3 REST API for the Pilar Estilo luxury boutique platform.

---

## Architecture Overview

Follows **Hexagonal Architecture** (Ports & Adapters). Domain purity rule:

> No Spring, JPA, or Lombok annotations permitted inside any `domain/` package. Domain models are plain Java.

Each feature is a self-contained module under `com.pilarestilo.<module>/`:

```
<module>/
  domain/
    model/          # Pure Java aggregates (no JPA)
    enums/          # Domain enumerations
    events/         # Domain event records (implement DomainEvent)
    ports/          # Repository and service interfaces
  application/
    usecases/       # Business orchestration (@Service)
    dto/            # Read-only output records
    commands/       # Input records for use cases
    mappers/        # Static domain → DTO converters
  infrastructure/
    persistence/
      entities/     # JPA @Entity classes
      repositories/ # Spring Data JPA + adapter implementing the port
    web/
      controllers/  # @RestController
      requests/     # Validated request records
    listeners/      # @EventListener cross-module handlers
    adapters/       # External service stubs/adapters
```

---

## Module Responsibilities

| Module | Responsibility |
|---|---|
| `shared/auth` | JWT issuance (HS256), BCrypt encoding, `JwtAuthenticationFilter`, auth use cases |
| `shared/domain` | `Money`, `DomainEvent` (sealed), `DomainException`, `DomainEventPublisher` port |
| `product` | Catalog CRUD, `avgRating`/`reviewCount` denormalization via `ReviewSummaryListener` |
| `category` | Category tree (parent + children), product taxonomy, `GET /api/categories/tree` |
| `review` | 1–5 star reviews, approval workflow, `ReviewSummaryListener` in product module |
| `order` | Order lifecycle state machine (CREATED → DELIVERED / CANCELLED) |
| `payment` | Manual payment proof workflow (PENDING → SUBMITTED → APPROVED / REJECTED) |
| `discount` | Promo codes — percentage and fixed, validation, usage tracking |
| `inventory` | Thin service delegating stock changes to `ProductRepository`, publishes `StockUpdated` |
| `user` | Customer/admin user accounts |
| `customercredit` | Per-customer store credit balance + movement history |
| `notification` | Log-based notifications triggered by domain events; swap adapter for email/SMS |

---

## Running Locally

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker (for Postgres via Compose, or Testcontainers)

### With Docker Compose (recommended)

```bash
cd infra
cp .env.example .env        # fill in JWT_SECRET and DB password
docker compose up -d postgres
cd ../backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Without Docker Compose

```bash
mvn spring-boot:run \
  -Dspring.profiles.active=local \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/pilarestilo \
  -Dspring.datasource.username=postgres \
  -Dspring.datasource.password=postgres \
  -DJWT_SECRET=your-secret-here
```

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/pilarestilo` |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `JWT_SECRET` | Yes | HS256 signing key — minimum 32 bytes. Generate: `openssl rand -base64 32` |
| `SERVER_PORT` | No (default 8080) | HTTP port |
| `SPRING_PROFILES_ACTIVE` | No | Set `local` for dev (relaxed DDL, debug logging) |

**Security:** never commit `JWT_SECRET`. Set in `infra/.env` (gitignored). In production, inject via your secrets manager or Docker secret.

---

## Auth

See `docs/auth.md` for the full JWT scheme, allowlist, and role matrix.

**Endpoints:**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create `CUSTOMER` account, returns JWT |
| `POST` | `/api/auth/login` | Returns access + refresh tokens |
| `POST` | `/api/auth/refresh` | Rotate access token |
| `GET` | `/api/auth/me` | Return current user info |

**Seed admin credentials:**

| Email | Password |
|---|---|
| `admin@pilarestilo.com` | `admin2026` |

**Promoting a new admin** (no endpoint by design — requires DB access):

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@domain.com';
```

---

## Running Tests

### Unit tests (no Docker)

```bash
mvn test
```

Runs `*Test.java` — pure JUnit 5 + Mockito, no Spring context.

### Full suite including integration tests (requires Docker)

```bash
mvn verify
```

`*IT.java` use **Testcontainers** to spin up PostgreSQL 16. Docker must be running.

---

## Domain Event Publisher — Swapping to Kafka

Currently synchronous via `SpringDomainEventPublisher`. To switch:

1. Add `spring-kafka` to `pom.xml`.
2. Create `KafkaDomainEventPublisher implements DomainEventPublisher` in `shared/infrastructure/`.
3. Mark it `@Primary`; remove `@Primary` from `SpringDomainEventPublisher`.
4. Update `@EventListener` subscribers to `@KafkaListener`.

No domain or application code changes required. See `docs/domain-events.md` for full migration guide and topic naming.

---

## Adding a New Module

Use `product` as the canonical template:

1. Create package tree: `com.pilarestilo.<module>/domain/`, `application/`, `infrastructure/`.
2. **Domain model** — pure Java, static `create(...)` factory, static `reconstruct(...)` for repo adapter.
3. **Domain events** — records implementing `DomainEvent`. Add to `permits` in `shared/domain/DomainEvent.java`.
4. **Port interface** — `domain/ports/<Module>Repository.java`.
5. **DTOs + mappers** — immutable records + static mapper.
6. **Use cases** — `@Service`, one `execute(...)` method each, `@Transactional` where needed.
7. **JPA entity** + **repository adapter** — maps entity ↔ domain.
8. **Controller** — `@RestController`, constructor injection, `@Valid` request bodies.
9. **Tests** — unit test for domain model, Mockito test for use case, `*IT.java` with Testcontainers.
