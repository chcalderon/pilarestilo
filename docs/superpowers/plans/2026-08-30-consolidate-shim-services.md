# Consolidate the four shim services back into the monolith — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this
> plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status 2026-08-30 (inline execution):**
> - **Task 1 (infra) — DONE**, commit `668361b`. Extra vs the plan: the 4 `APP_*_REMOTE_ENABLED`
>   flags are pinned to `"false"` **directly in `docker-compose.yml`** (not `${VAR:-false}`), so a
>   stale `=true` in the VPS `.env` can't make the monolith delegate to a deleted service. The
>   `.env`/`.env.example` flag lines were removed (compose is the source of truth now).
> - **Task 2 (delete codebases + CI) — DONE**, commit `8efbce9`. notification-service gained a
>   Dependabot entry it was missing; CodeQL matrix now names it.
> - **Task 3 (docs) — DONE**, commit `831f252`. Also touched `docs/roadmap.md` (P6 revert note),
>   `docs/notification-database-split.md` (historical header) and the discount-code note in
>   `docs/architecture.md`.
> - **Task 4 — Step 1 (local smoke) PASS**: only `pe_notification_service` among the extracted;
>   14 purchases to DELIVERED, 0 failed, 68/68 checks, 0 backend errors; `docker stats` all under
>   `mem_limit`. Step 2 skipped (backend/ unchanged). Steps 3–4 (merge → master deploy + prod
>   smoke) pending the owner's go.
> - **Phase 2** (delete the monolith's dormant `order`/`payment` remote clients + the
>   inventory remote-write branch — touches `backend/src`, needs the real `mvn verify`) is a
>   separate later commit.

**Goal:** Stop deploying `product-service`, `inventory-service`, `order-service` and
`payment-service`; the monolith serves all of their routes already.

**Architecture:** Each of the four is a thin layer over the *same* `pilarestilo` database the
monolith owns — no independent data, no independent scaling benefit, and the source of five
documented "schema changed on one side only" production bugs (`order-service-writes-orders-too`
memory). The monolith's non-remote path is the default (`app.*.remote.enabled` defaults to
`false` in `application.yml`) and is already the Caddy fallback (`reverse_proxy … backend:8080`),
so this is a config + infra removal, not a code change to `backend/`. `notification-service`
stays — it has its own database and is Kafka-only, a real extraction.

The 2026-08-30 load test (`scripts/loadtest/`) settled the capacity question: 9 concurrent full
purchase flows ran at 8.8 req/s, load 2.5 on 6 cores, p95 281 ms, Hikari pool never queued.
There is no throughput case for these services at this scale.

**Tech Stack:** Docker Compose, Caddy, Spring Boot 4.1, GitHub Actions, Prometheus.

**Spec:** none written separately — the decision came out of the 2026-08-30 microservices impact
analysis (`monolith-dissolution-direction` / `vps-undersized-swap-added` memories). This plan is
the record.

## Global Constraints

- `backend/` is **not** touched. No migration, no `backend/src` edit. If a change seems to need
  one, stop — the assumption is wrong.
- `notification-service` and everything about it is left exactly as is.
- The monolith's dormant remote-client code (`backend/.../order/application/remote/`,
  `payment/application/remote/`, the remote-write branch in `inventory/application/InventoryService.java`,
  the `app.*.remote` keys in `application.yml` + `additional-spring-configuration-metadata.json`)
  is **kept** in this plan — gated off by the now-false flags, same as notification T14→T16. A
  follow-up (§ Phase 2) deletes it once prod has run clean for a few days.
- Rollback for the whole plan = `git revert` the commits + redeploy. There is no data to preserve
  (unlike the notification extraction) because the four services own none.
- `DEPLOY_PROFILES` keeps `microservices` — `notification-service` runs under that profile.
- Deploy is `develop` → `master`; the push to `master` deploys. The owner triggers it.

---

## Task 1: Remove the four services from the running stack (infra)

**Files:**
- Modify: `infra/.env`
- Modify: `infra/.env.example`
- Modify: `infra/docker-compose.yml`
- Modify: `infra/Caddyfile`
- Modify: `infra/monitoring/prometheus/prometheus.yml`

**Interfaces:**
- Produces: a compose stack whose only `services/*` container is `notification-service`; Caddy
  routes every `/api/*` path to `backend:8080` except `GET/HEAD/PUT /api/notifications*`
  (→ `notification-service:8085`, unchanged) and the two `/api/admin/product-ai/*-single` routes
  (→ `backend`, unchanged).

- [ ] **Step 1: `infra/.env` — flip the delegation flags off, drop the now-unused vars.**

  Set these four to `false`:
  ```
  APP_INVENTORY_REMOTE_ENABLED=false
  APP_ORDER_REMOTE_ENABLED=false
  APP_ORDER_REMOTE_WRITE_ENABLED=false
  APP_PAYMENT_REMOTE_ENABLED=false
  ```
  Delete these lines (only the monolith→service wiring — nothing else reads them once the flags
  are false and the containers are gone):
  ```
  APP_INVENTORY_REMOTE_BASE_URL=http://inventory-service:8082
  APP_ORDER_REMOTE_BASE_URL=http://order-service:8083
  APP_ORDER_REMOTE_SERVICE_TOKEN=order-service-internal-token
  APP_PAYMENT_REMOTE_BASE_URL=http://payment-service:8084
  APP_PAYMENT_REMOTE_SERVICE_TOKEN=payment-service-internal-token
  ```
  Update the comment above the block (currently "Enable remote delegation flags only when running
  with DEPLOY_PROFILES=microservices.") to:
  ```
  # The extracted read/write shims (product/inventory/order/payment-service) were consolidated
  # back into the monolith on 2026-08-30. The monolith serves these routes itself; these flags
  # stay at false. Only notification-service remains under the microservices profile.
  ```
  Leave `DEPLOY_PROFILES=microservices,cache,observability,tracing,kafka` unchanged.

- [ ] **Step 2: `infra/.env.example` — mirror Step 1** (same four flags to `false`, same five
  lines deleted, same comment).

- [ ] **Step 3: `infra/docker-compose.yml` — delete four service blocks.**

  Remove the entire `product-service:`, `inventory-service:`, `order-service:` and
  `payment-service:` service definitions (from each `  <name>:` line through the blank line before
  the next service). Keep `notification-service:` and every non-service block. The heap-cap
  `mem_limit` / `JAVA_TOOL_OPTIONS` lines on `backend`, `kafka` and `notification-service` stay.

- [ ] **Step 4: `infra/Caddyfile` — delete the four routing blocks.**

  Remove these named matchers and their `handle` blocks:
  `@product_review_reads` (only existed to shield `@product_reads` — redundant once that is gone;
  `/api/products/*/reviews` then falls through the generic `/api/*` to `backend`),
  `@product_reads`, `@inventory_reads`, `@payment_reads`, `@order_reads`, `@order_writes`,
  `@order_disallowed`.
  Keep `@notification_reads` and its `handle` (unchanged), the two `@product_ai_*_single` blocks,
  the generic gateway policy blocks (`@api_paths`, `@api_invalid_method`, rate limits), and the
  final `handle /api/* { reverse_proxy backend:8080 }` + `handle { reverse_proxy frontend:4321 }`
  catch-alls.

- [ ] **Step 5: `infra/monitoring/prometheus/prometheus.yml` — drop four scrape jobs.**

  Remove the `product_service`, `inventory_service`, `order_service` and `payment_service`
  `job_name` blocks. Keep `backend` and `notification_service`.

- [ ] **Step 6: Validate the config.**

  ```bash
  docker compose --project-directory infra --env-file infra/.env.example config --quiet && echo "compose OK"
  docker run --rm -v "$PWD/infra":/w -w /w caddy:2-alpine caddy validate --config Caddyfile
  python -c "import yaml; yaml.safe_load(open('infra/monitoring/prometheus/prometheus.yml')); print('prometheus yaml OK')"
  ```
  Expected: all three print OK / "Valid configuration".

- [ ] **Step 7: Bring the stack up locally and verify the monolith serves everything.**

  ```bash
  # stop any running stack first if Testcontainers or an old stack is up
  cd infra && docker compose --env-file .env \
    --profile kafka --profile cache --profile microservices --profile observability --profile tracing \
    up -d --build --remove-orphans
  ```
  Wait for `infra-backend-1` and `pe_notification_service` healthy, then:
  ```bash
  docker ps --format '{{.Names}}' | grep -E '_service'      # expect ONLY pe_notification_service
  curl -sk https://localhost/api/actuator/health            # UP
  curl -sk -o /dev/null -w '%{http_code}\n' https://localhost/api/products         # 200 (served by backend)
  curl -sk -o /dev/null -w '%{http_code}\n' https://localhost/api/notifications/_health  # 200
  ```
  Then run one real order end to end — the load-test smoke is the fastest way:
  ```bash
  bash scripts/loadtest/prep.sh
  MSYS_NO_PATHCONV=1 docker run --rm --network infra_pe_net -e BASE_URL=http://backend:8080/api \
    -e BUYERS=1 -e HOLD=30s -e ADMIN_DURATION=90s -v "$PWD/scripts/loadtest":/lt \
    grafana/k6 run /lt/purchase-flow.js
  bash scripts/loadtest/cleanup.sh
  ```
  Expected: `lt_purchase_complete` ≥ 1, `lt_purchase_failed` 0, `http_req_failed` 0% — the
  monolith now writes `orders` and serves product/inventory/payment reads itself.

- [ ] **Step 8: Commit.**

  ```bash
  git add infra/
  git commit -m "infra: stop deploying the four shim services — monolith serves their routes"
  ```

---

## Task 2: Delete the four service codebases and their CI

**Files:**
- Delete: `services/product-service/`, `services/inventory-service/`, `services/order-service/`,
  `services/payment-service/`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/codeql.yml`
- Modify: `.github/dependabot.yml`

**Interfaces:**
- Produces: `services/` containing only `notification-service/`; CI's `service-tests`,
  CodeQL's matrix and Dependabot's Maven entries reference only `notification-service`.

- [ ] **Step 1: Remove the directories.**

  ```bash
  git rm -r services/product-service services/inventory-service services/order-service services/payment-service
  ```

- [ ] **Step 2: `.github/workflows/ci.yml` — shrink the `service-tests` matrix.**

  Change `matrix.service` to `[notification-service]`. Replace the block comment above
  `service-tests:` (the one starting "The four extracted services were never built here.") with:
  ```yaml
  # notification-service is the one remaining extracted service (its own DB, Kafka-only). Built
  # and tested here; product/inventory/order/payment-service were consolidated back into the
  # monolith on 2026-08-30.
  ```

- [ ] **Step 3: `.github/workflows/codeql.yml` — remove four matrix entries.**

  Delete the `- name: order-service` / `inventory-service` / `product-service` / `payment-service`
  `include` entries (each is 3 lines: `name`, `language`, `working-directory`). Keep `backend`,
  `frontend` and — if present — `notification-service`. If `notification-service` is **not**
  already in the matrix, add it:
  ```yaml
          - name: notification-service
            language: java-kotlin
            working-directory: services/notification-service
  ```

- [ ] **Step 4: `.github/dependabot.yml` — remove four Maven entries.**

  Delete the four `- package-ecosystem: maven` blocks whose `directory:` is
  `/services/order-service`, `/services/inventory-service`, `/services/product-service`,
  `/services/payment-service` (each block runs from its `- package-ecosystem:` line to the line
  before the next `- package-ecosystem:`). Keep `/backend`, `/frontend`, and add or keep one for
  `/services/notification-service` (copy the shape of the deleted `order-service` block, swap the
  path and the group name to `notification-service-minor-patch`). Trim any now-stale wording in
  the file's top comment that names the deleted services.

- [ ] **Step 5: Verify.**

  ```bash
  cd services/notification-service && mvn -o -q -DskipTests package && cd -   # still builds
  python -c "import yaml,glob; [yaml.safe_load(open(f)) for f in glob.glob('.github/**/*.yml',recursive=True)]; print('workflow yaml OK')"
  ls services/                                                                # only notification-service/
  grep -rn 'product-service\|inventory-service\|order-service\|payment-service' .github/ | grep -v java-upgrade || echo "no live .github refs"
  ```
  Expected: build succeeds, yaml parses, `services/` has one entry, no live `.github` references
  (the `.github/java-upgrade/` archive may still mention them — that is a historical log, leave it).

- [ ] **Step 6: Commit.**

  ```bash
  git add services/ .github/
  git commit -m "chore: delete the four consolidated shim services + their CI"
  ```

---

## Task 3: Documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/architecture.md`
- Modify: `docs/deployment.md`
- Modify: `docs/notification-database-split.md`

- [ ] **Step 1: `CLAUDE.md`.**

  - Monorepo layout block: change the `services/` line to
    `services/         Extracted microservice (P6 — optional profile)` and list only
    `notification-service`.
  - "Caddy routing" table: remove the `product-service`, `inventory-service`, `payment-service`
    and `order-service` rows. Keep the `notification-service` row. Change the intro sentence from
    "read paths are routed to extracted services" to "the notification read/mark-as-read paths are
    routed to notification-service; everything else is served by the monolith."
  - "Two codebases write the `orders` table" section: rewrite. `order-service` no longer exists;
    the monolith writes `orders` directly again. Keep the paragraph about `notification-service`
    being a **read-only** third party mapping `insertable=false` views of
    `orders`/`order_items`/`users`/`payments`/`sales_documents`/`return_requests`/`system_settings`
    — that coupling is unchanged, and the `*RoEntity` / `ReadOnlyMappingIT` rule still applies.
    Retitle the section "notification-service reads the monolith's tables".
  - "Key env vars" table: drop the `APP_INVENTORY_REMOTE_ENABLED`, `APP_ORDER_REMOTE_ENABLED`,
    `APP_PAYMENT_REMOTE_ENABLED` rows. Keep `APP_DOMAIN_EVENTS_KAFKA_ENABLED`, `APP_CACHE_REDIS_ENABLED`,
    `APP_TRACING_ENABLED`.
  - The `.env.example` paragraph near the top: it lists `the three APP_*_REMOTE_ENABLED flags` as
    shipped-on — change to note they are now shipped **off** and only `notification-service`'s
    profile remains.

- [ ] **Step 2: `docs/architecture.md`.**

  - Monorepo layout (lines ~18-25): `services/` lists only `notification-service/`.
  - Line ~284 profiles: `microservices` now "adds `notification-service`".
  - Caddy routing (lines ~291-292 and any sibling rows): remove `product-service` /
    `inventory-service` / `order-service` / `payment-service` rows, keep notification.
  - Line ~322 "inventory-service now exposes stock command endpoints" and line ~358
    "product-service supports optional read-replica routing" — delete these bullets.
  - Line ~368 tracing: "Backend and `notification-service` emit OTLP traces".
  - Coverage tables (lines ~389-401): remove the `services/inventory-service`,
    `services/product-service`, `services/order-service`, `services/payment-service` rows; keep
    `services/notification-service`.

- [ ] **Step 3: `docs/deployment.md`.**

  - Caddy routing (lines ~217-218 + siblings): remove the shim rows, keep notification.
  - Lines ~244 (inventory `reserve/release/confirm` delegation) and ~259 (`product-service`
    read-replica) — delete.
  - Health-check curl list (lines ~306, ~313): remove the `order-service:8083` and
    `payment-service:8084` curls; keep `backend` and `notification-service:8085`.

- [ ] **Step 4: `docs/notification-database-split.md`.**

  Lines ~14-15 table: remove the `inventory-service` and `product-service` rows (they no longer
  exist). Keep the `notification-service` row.

- [ ] **Step 5: Verify + commit.**

  ```bash
  grep -rn 'product-service\|inventory-service\|order-service\|payment-service' CLAUDE.md docs/*.md \
    | grep -v 'superpowers/' || echo "docs clean"
  git add CLAUDE.md docs/
  git commit -m "docs: consolidate the shim services out of the architecture docs"
  ```
  (`docs/superpowers/` plans/specs are historical — leave them.)

---

## Task 4: Full-stack verification and deploy

**Files:** none (verification + merge only).

- [ ] **Step 1: Fresh full-stack smoke, local.**

  With the stack from Task 1 Step 7 still up (rebuilt), or bring it up again the same way. Confirm:
  ```bash
  docker ps --format '{{.Names}} {{.Status}}'          # no pe_product/inventory/order/payment_service
  docker compose --project-directory infra --env-file infra/.env ps
  ```
  Run the load-test **smoke** again (`BUYERS=3 HOLD=60s`), expect 0 failures, and eyeball
  `docker logs infra-backend-1` for a clean boot — one Hikari pool, Flyway validated, no
  `remote` / `RestClient` wiring for order/payment/inventory, no errors.

- [ ] **Step 2: Backend test suite — skip, unchanged.**

  `backend/` has no diff in this plan. Its `mvn clean verify` passed twice for the notification
  T16 work with `app.*.remote.enabled` at their `false` defaults — that *is* the consolidated
  path. Note this in the PR/commit; do not spend the 15 minutes.

- [ ] **Step 3: Merge to develop, then master (deploy).**

  ```bash
  git checkout develop && git merge --ff-only <task branch>   # or the commits are already on develop
  git checkout master && git merge --ff-only develop
  git push origin master        # deploys
  git push origin develop
  ```
  Watch: `gh run watch <CI id> --exit-status` then the `Deploy VPS` run. Both must be `success`.
  A failed CI means the deploy did not run.

- [ ] **Step 4: Post-deploy prod smoke.**

  ```bash
  curl -s https://pilarestilo.com/api/actuator/health                       # UP (after the ~40s restart window)
  curl -s https://pilarestilo.com/api/notifications/_health                 # UP
  curl -s -o /dev/null -w '%{http_code}\n' https://pilarestilo.com/api/products   # 200
  ```
  Then place one real order against prod (or run the load-test smoke pointed at prod with
  `prep`/`cleanup` on the prod DB) and confirm it reaches DELIVERED, the boleta + dispatch work,
  and exactly one notification set fires from `notification-service`. Prod's Prometheus
  (`/api/actuator/prometheus`) should show the monolith Hikari pool healthy and no
  `order`/`payment`/`inventory` remote-client beans.

- [ ] **Step 5: Update memories.**

  `monolith-dissolution-direction`, `vps-undersized-swap-added`, `pending-work-queue`,
  `order-service-writes-orders-too` (the "two codebases write orders" hazard is retired for
  order-service — note it, keep the notification-service RO-reader caveat),
  `three-codebases-answer-stock` (down to two: monolith + the browser).

---

## Phase 2 — delete the monolith's dormant remote-client code (separate, later)

**Not part of this plan's deploy.** After prod has run consolidated and clean for a few days,
one more commit removes what is now unreachable:

- `backend/src/main/java/com/pilarestilo/order/application/remote/` (`OrderRemoteCommandClient`,
  `OrderRemoteQueryClient`) + their DTOs/mappers + tests
- `backend/src/main/java/com/pilarestilo/payment/application/remote/` (`PaymentRemoteQueryClient`)
  + tests
- the remote-write branch + `@Value("${app.inventory.remote.enabled…}")` constructor arg in
  `backend/src/main/java/com/pilarestilo/inventory/application/InventoryService.java`
- the `app.order.remote.*`, `app.payment.remote.*`, `app.inventory.remote.*` blocks in
  `backend/src/main/resources/application.yml` and their
  `additional-spring-configuration-metadata.json` entries
- `RestClient.Builder` usages that were only for these — grep before removing
  `spring-boot-restclient` from `backend/pom.xml` (notification-service is a separate module; the
  monolith may still use `RestClient` for the payment gateway — check).

`mvn clean verify` (the real 15-minute run) is mandatory for Phase 2 because it *does* touch
`backend/src`.

---

## Self-Review

**Coverage of the decision:** stop deploying the 4 shims (Tasks 1–2) ✅; keep notification-service
(Global Constraints, every task) ✅; monolith serves the routes (Task 1 Step 7, Task 4) ✅; docs
(Task 3) ✅; deploy + verify (Task 4) ✅; dormant-code cleanup deferred, not dropped (Phase 2) ✅.

**Placeholder scan:** every step names exact files and exact commands; no "handle edge cases" /
"TBD". The Caddyfile/compose edits describe the blocks by their unique matcher/service names.

**Consistency:** flag names (`APP_INVENTORY_REMOTE_ENABLED`, `APP_ORDER_REMOTE_ENABLED`,
`APP_ORDER_REMOTE_WRITE_ENABLED`, `APP_PAYMENT_REMOTE_ENABLED`) match `infra/.env` lines 115/119/122/128
and `application.yml` lines 128/143/147 as read on 2026-08-30. `infra_pe_net` is the compose
network name (project `infra`). `notification-service` port 8085, profile `microservices` — unchanged.
