# Pilar Estilo — Deployment Guide

This runbook covers deploying Pilar Estilo to a Contabo-class VPS from scratch, updating the app, backup, monitoring, and local development without Docker.

---

## 1. Server Requirements

| Resource | Minimum | Recommended |
|---|---|---|
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| vCPUs | 2 | 4 |
| RAM | 4 GB | 8 GB |
| SSD | 50 GB | 100 GB |
| Open ports | 22 (SSH), 80 (HTTP), 443 (HTTPS) | same |

Contabo VPS S (4 vCPU, 6 GB RAM, 100 GB SSD NVMe) is the recommended entry-level tier. The Spring Boot JVM requires at least 512 MB heap; leave headroom for PostgreSQL and Caddy.

---

## 2. Install Docker and Docker Compose

SSH into the server as root or a sudo user, then run:

```bash
# Update package index
sudo apt-get update

# Install prerequisites
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine and Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

# Allow your deploy user to run Docker without sudo (log out and back in after this)
sudo usermod -aG docker $USER

# Verify
docker --version
docker compose version
```

---

## 3. Clone the Repository

```bash
# On the server
cd /opt
sudo git clone https://github.com/YOUR_ORG/pilarestilo.git
sudo chown -R $USER:$USER /opt/pilarestilo
cd /opt/pilarestilo
```

If you use a private repo, set up a deploy key:
```bash
ssh-keygen -t ed25519 -C "deploy@pilarestilo" -f ~/.ssh/deploy_key -N ""
# Add the public key to GitHub → Settings → Deploy keys
```

---

## 4. Configure Environment

```bash
cp /opt/pilarestilo/infra/.env.example /opt/pilarestilo/infra/.env
nano /opt/pilarestilo/infra/.env
```

Edit the following values at minimum:

```bash
POSTGRES_PASSWORD=a_long_random_password_here   # generate with: openssl rand -base64 32
JWT_SECRET=                                     # generate with: openssl rand -base64 32
SYSTEM_SETTINGS_CRYPTO_SECRET=                  # generate with: openssl rand -base64 32
DOMAIN=pilarestilo.com                          # your real domain

# Docker Compose profiles activated by vps_deploy.sh.
# Standard full stack (includes extracted microservices + Redis):
DEPLOY_PROFILES=microservices,cache

# Enable backend-to-microservice delegation once microservices are running:
APP_INVENTORY_REMOTE_ENABLED=true
APP_ORDER_REMOTE_ENABLED=true
APP_ORDER_REMOTE_WRITE_ENABLED=true
APP_PAYMENT_REMOTE_ENABLED=true
APP_CACHE_REDIS_ENABLED=true

# Optional Kafka domain-events mode:
# APP_DOMAIN_EVENTS_KAFKA_ENABLED=true
# DEPLOY_PROFILES=microservices,cache,kafka

# Dispatch auto-delivery scheduler (default: every 30 minutes):
# APP_DISPATCH_AUTO_DELIVERY_CRON=0 */30 * * * *
```

Protect the file from other users:
```bash
chmod 600 /opt/pilarestilo/infra/.env
```

Never commit `.env` to version control. It is listed in `.gitignore`.

---

## 5. DNS Configuration

In your domain registrar's DNS panel, create an **A record** pointing to the VPS public IP:

```
Type:  A
Name:  @  (or pilarestilo.com)
Value: <VPS_PUBLIC_IP>
TTL:   300
```

If you want `www` to also work:
```
Type:  CNAME
Name:  www
Value: pilarestilo.com
TTL:   300
```

DNS propagation typically takes 1–15 minutes but can take up to 48 hours. Verify propagation before proceeding:

```bash
dig +short pilarestilo.com
# Should return your VPS IP
```

---

## 6. First Deploy

```bash
cd /opt/pilarestilo
bash scripts/deploy/vps_deploy.sh
```

The script:
1. Reads `DEPLOY_PROFILES` from `infra/.env` (or shell env) to activate optional profiles.
2. Syncs the repo (`git pull --ff-only`).
3. Builds and starts all services for the active profiles.
4. Flyway migrations run automatically on backend startup.
5. Product media is mounted from `infra/storage/media` → `/app/media`.

Important: `DEPLOY_PROFILES` is interpreted by `scripts/deploy/vps_deploy.sh` and `scripts/deploy/local_deploy.sh`.
If you run `docker compose ... up` directly, Docker Compose does not read `DEPLOY_PROFILES` automatically.

To override profiles for a single run without editing `.env`:
```bash
DEPLOY_PROFILES=microservices,cache bash scripts/deploy/vps_deploy.sh
```

Manual profile examples (without deploy script):
```bash
# Extracted microservices (product / inventory / order / payment)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile microservices up -d --build

# Redis cache
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile cache up -d redis

# Kafka broker for domain-events mode
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile kafka up -d

# Distributed tracing stack (OTel Collector + Tempo)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile tracing up -d

# Horizontal backend scale behind Caddy
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --scale backend=2
```

With `microservices` profile enabled, Caddy routes:
- `GET/HEAD /api/products*` to `product-service` (with `backend:8080` fallback, 2 s dial / 30 s response timeout)
- `GET/HEAD /api/inventory*` to `inventory-service` (with `backend:8080` fallback)
- `GET/HEAD /api/payments*` to `payment-service` (with `backend:8080` fallback)
- `GET/HEAD /api/orders*` to `order-service` (with `backend:8080` fallback)
- `POST/PATCH /api/orders*` to `backend:8080` only (auth/orchestration entrypoint)
- All remaining `/api/*` traffic to `backend:8080` via round-robin DNS (`dynamic a backend 8080`)

Additional gateway policies:
- `/api/*` request body cap: `12MB`
- unsupported HTTP methods rejected with `405` (checked before routing)
- `/api/orders*` only allows `GET|HEAD|POST|PATCH` at gateway (PUT/DELETE/etc. return 405)

Redis cache baseline:

- Backend uses in-memory cache by default for hot-read endpoints.
- Set `APP_CACHE_REDIS_ENABLED=true` to move cache storage to Redis.
- Redis service is optional and started through `--profile cache`.

Horizontal backend scaling baseline:

- Caddy resolves backend upstreams dynamically by Docker DNS (`dynamic a backend 8080`).
- Scale backend instances with Docker Compose:
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --scale backend=2`
- Scale down to single replica:
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --scale backend=1`

When `APP_INVENTORY_REMOTE_ENABLED=true`, backend inventory write commands
(`reserve/release/confirm`) are also delegated to `inventory-service` through
internal service-to-service HTTP (`APP_INVENTORY_REMOTE_BASE_URL`).

When `APP_ORDER_REMOTE_ENABLED=true`, backend order reads are delegated to
`order-service` through internal service-to-service HTTP (`APP_ORDER_REMOTE_BASE_URL`).

When `APP_ORDER_REMOTE_WRITE_ENABLED=true`, backend order create/status updates
are delegated to `order-service` through internal service-to-service HTTP
(`APP_ORDER_REMOTE_BASE_URL`).

When `APP_PAYMENT_REMOTE_ENABLED=true`, backend payment reads are delegated to
`payment-service` through internal service-to-service HTTP (`APP_PAYMENT_REMOTE_BASE_URL`).
If internal auth is enabled there, backend must also provide
`APP_PAYMENT_REMOTE_SERVICE_TOKEN` (`X-Service-Token`).

When `APP_DB_READ_REPLICA_ENABLED=true`, `product-service` routes read-only
catalog queries to the configured replica datasource (`APP_DB_READ_REPLICA_*`).
If disabled, catalog reads continue using the primary Postgres datasource.

The first build takes 3–8 minutes depending on server speed and image cache state.

---

## 7. Verify

**Check container health:**
```bash
docker compose -f infra/docker-compose.yml ps
# All services should show "healthy" or "running"
```

**Check backend health endpoint:**
```bash
curl -f http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
# (This hits the backend directly from the server; bypass Caddy for this check)
```

**Check through Caddy (HTTPS):**
```bash
curl -f https://pilarestilo.com/api/actuator/health
# Expected: {"status":"UP"}
```

**Check catalog read routing through Caddy (when `microservices` profile is enabled):**
```bash
curl -i http://localhost/api/products/_health
# Expected: HTTP/1.1 204 No Content

curl -f "http://localhost/api/products?page=0&size=1"
# Expected: 200 with paged JSON payload

curl -i http://localhost/api/inventory/_health
# Expected: HTTP/1.1 204 No Content

curl -f "http://localhost/api/inventory/products?page=0&size=1"
# Expected: 200 with paged JSON payload
```

**Check extracted order-service health (internal container network):**
```bash
docker run --rm --network infra_pe_net curlimages/curl:8.7.1 \
  -s -o /dev/null -w "%{http_code}\n" http://order-service:8083/api/orders/_health
# Expected: 204
```

**Check extracted payment-service health (internal container network):**
```bash
docker run --rm --network infra_pe_net curlimages/curl:8.7.1 \
  -s -o /dev/null -w "%{http_code}\n" http://payment-service:8084/api/payments/_health
# Expected: 204
```

**Check logs for errors:**
```bash
docker compose -f infra/docker-compose.yml logs --tail=50 backend
docker compose -f infra/docker-compose.yml logs --tail=50 caddy
```

---

## 8. TLS / HTTPS

Caddy handles TLS automatically. When `DOMAIN=pilarestilo.com` is set in `.env`:

1. On first startup, Caddy contacts Let's Encrypt and requests a certificate for `pilarestilo.com`.
2. Let's Encrypt performs an HTTP-01 challenge (hits port 80 on your domain — this is why DNS must point to the server before first deploy).
3. Caddy stores the certificate in the `caddy_data` Docker volume (persisted across restarts).
4. Caddy renews the certificate automatically before expiry (Let's Encrypt certs expire every 90 days).

No `certbot`, no cron jobs, no manual renewal needed.

**Rate limits:** Let's Encrypt limits certificate issuance to 5 duplicate certificates per week per domain. Do not destroy and recreate the `caddy_data` volume repeatedly in production.

---

## 9. Updating the Application

```bash
cd /opt/pilarestilo
bash scripts/deploy/vps_deploy.sh
```

For automated VPS deploys using GitHub Actions (after CI success on `master` or manual dispatch),
see: `docs/github-actions-vps.md`.

Docker Compose rebuilds only the services whose image changed and replaces containers one at a time. There is a brief downtime window (~5–15 seconds) while backend replicas restart.

**Lower-downtime strategy (available):** run at least two backend replicas behind Caddy before updating:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --scale backend=2
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build backend caddy
```

**Database migrations:** Flyway runs automatically on backend startup. Migrations are applied in order and are idempotent — re-running them on an already-migrated database is safe.

**Rollback:** To revert to the previous image, use `git checkout <previous-commit>` and re-run `docker compose up -d --build`.

---

## 10. Backup Strategy

### Manual backup

```bash
docker exec pe_postgres pg_dump -U pilar pilarestilo > /opt/backups/pilarestilo_$(date +%Y%m%d_%H%M%S).sql
tar -czf /opt/backups/pilarestilo_media_$(date +%Y%m%d_%H%M%S).tar.gz -C /opt/pilarestilo/infra/storage media
```

### Automated backup with cron

```bash
# Create backup directory
sudo mkdir -p /opt/backups
sudo chown $USER:$USER /opt/backups

# Edit crontab
crontab -e
```

Add this line to run a backup every day at 02:00 and keep the last 30 days:

```cron
0 2 * * * docker exec pe_postgres pg_dump -U pilar pilarestilo > /opt/backups/pilarestilo_$(date +\%Y\%m\%d_\%H\%M\%S).sql && find /opt/backups -name "*.sql" -mtime +30 -delete
10 2 * * * tar -czf /opt/backups/pilarestilo_media_$(date +\%Y\%m\%d_\%H\%M\%S).tar.gz -C /opt/pilarestilo/infra/storage media && find /opt/backups -name "pilarestilo_media_*.tar.gz" -mtime +30 -delete
```

### Offsite backup

Copy backups to an S3-compatible bucket (Contabo Object Storage, Backblaze B2, AWS S3):

```bash
# Install rclone
curl https://rclone.org/install.sh | sudo bash
rclone config  # configure your S3 bucket

# Add to cron after the pg_dump line:
rclone copy /opt/backups/ remote:pilarestilo-backups/
```

---

## 11. Monitoring

**Real-time logs:**
```bash
# All services
docker compose -f infra/docker-compose.yml logs -f

# Specific service
docker compose -f infra/docker-compose.yml logs -f backend
docker compose -f infra/docker-compose.yml logs -f caddy
```

**Spring Actuator endpoints** (available at `/api/actuator/`):

| Endpoint | Description |
|---|---|
| `/api/actuator/health` | Liveness + readiness, includes DB connectivity |
| `/api/actuator/info` | App version, build info |
| `/api/actuator/metrics` | JVM metrics, HTTP request counts, DB pool stats |

**Prometheus + Grafana (P7 baseline):**

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile observability up -d
```

- Prometheus UI: `http://<host>:9090`
- Grafana UI: `http://<host>:3000` (defaults from `.env`: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`)
- Backend metrics endpoint: `https://<domain>/api/actuator/prometheus`

**Distributed tracing (OpenTelemetry baseline):**

- Set in `.env`:
  - `APP_TRACING_ENABLED=true`
  - `APP_TRACING_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces`
  - `APP_TRACING_SAMPLING_PROBABILITY=1.0` (adjust lower in production)
- Start with either:
  - `--profile tracing` (collector + tempo only), or
  - `--profile observability` (includes tracing stack plus Prometheus/Grafana).
- Tempo API: `http://<host>:3200`
- In Grafana, use provisioned `Tempo` datasource to explore traces.

---

## 12. Local Development

### Without Docker

**Backend:**
```bash
# Prerequisites: Java 25+, Maven 3.9+, PostgreSQL running locally

cd backend/
mvn spring-boot:run -Dspring-boot.run.profiles=local
# Uses src/main/resources/application-local.yml
# Connects to localhost:5432/pilarestilo by default
```

Ensure a local PostgreSQL database exists:
```bash
createdb pilarestilo
createuser pilar --password  # set password to 'pilar' for local dev
psql -c "GRANT ALL PRIVILEGES ON DATABASE pilarestilo TO pilar;"
```

**Frontend:**
```bash
# Prerequisites: Node.js 20+

cd frontend/
npm install
npm run dev
# Starts Astro dev server at http://localhost:4321
# Set PUBLIC_API_BASE_URL=http://localhost:8080/api in frontend/.env
```

### With Docker (recommended for full-stack testing)

```bash
# From repo root
bash scripts/deploy/local_deploy.sh up
# or in PowerShell
powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 up
```

Access the app at `http://localhost` (Caddy on port 80 with `DOMAIN=localhost`).
Both local deploy scripts are local-only by design and will fail if `DOMAIN` is not local (`localhost`, `127.0.0.1`, `::1`, or `*.localhost`).

### Local Docker quick checklist

```bash
# 1) Start/update stack
bash scripts/deploy/local_deploy.sh up
# powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 up

# 2) Check service status
bash scripts/deploy/local_deploy.sh ps
# powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 ps

# 3) Follow logs (all or targeted)
bash scripts/deploy/local_deploy.sh logs
bash scripts/deploy/local_deploy.sh logs backend frontend caddy
# powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 logs
# powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 logs backend frontend caddy

# 4) Stop stack (keep volumes)
bash scripts/deploy/local_deploy.sh down
# powershell -ExecutionPolicy Bypass -File scripts/deploy/local_deploy.ps1 down
```

---

## 13. Troubleshooting

### Caddy cannot get a TLS certificate

**Symptom:** `caddy` logs show `failed to obtain certificate` or `ACME challenge failed`.

**Causes and fixes:**
- DNS A record not yet pointing to this server — wait for propagation and restart Caddy: `docker compose restart caddy`.
- Port 80 blocked by a firewall — open port 80: `sudo ufw allow 80/tcp`.
- Let's Encrypt rate limit hit — wait and check [https://certs.certspotter.com/](https://certs.certspotter.com/) for issued certs.
- Running with `DOMAIN=localhost` — Caddy uses a self-signed cert for localhost; browsers will warn. Use `http://localhost` or trust the cert in your OS.

### Backend health check fails / container keeps restarting

**Symptom:** `backend` service containers show `unhealthy` or restart loops.

**Causes and fixes:**
- Database not ready — check `pe_postgres` logs: `docker compose logs postgres`. Wait for `database system is ready to accept connections`.
- Flyway migration error — check backend logs for `FlywayException`. A failed migration will prevent startup. Fix the migration file and restart.
- Out of memory — check `docker stats`. Increase server RAM or add `JAVA_OPTS=-Xmx512m` to backend environment in `docker-compose.yml`.
- Wrong database credentials — verify `POSTGRES_USER` and `POSTGRES_PASSWORD` match in `infra/.env`.

### Flyway migration error

**Symptom:** Backend logs show `Migration V{n}__ ... failed` or `validate failed: detected failed migration`.

**Fix:**
```bash
# Connect to the database
docker exec -it pe_postgres psql -U pilar -d pilarestilo

# Check failed migrations
SELECT * FROM flyway_schema_history WHERE success = false;

# If in development: delete the failed record and fix the migration file
DELETE FROM flyway_schema_history WHERE success = false;
\q

# Restart backend
docker compose restart backend
```

In production, always review and test migrations against a staging database before deploying.

### Frontend shows old version after update

**Symptom:** After `docker compose up -d --build`, the frontend still serves cached content.

**Fix:** Force a full rebuild:
```bash
docker compose -f infra/docker-compose.yml build --no-cache frontend
docker compose -f infra/docker-compose.yml up -d frontend
```

Also clear your browser cache or use a hard refresh (`Ctrl+Shift+R`).

### Payment notifications are created but email is not delivered

**Symptom:** order/payment flow works and in-app notification appears, but no email arrives.

**Quick checks:**

1. Ensure notification provider is `EMAIL_SMTP` or `EMAIL_SENDGRID` in admin settings (`/admin/settings`).
2. Check backend logs:
   ```bash
   docker compose -f infra/docker-compose.yml logs --tail=120 backend
   ```
   Look for:
   - `[EMAIL:SMTP] send failed ...`
   - `[EMAIL:SENDGRID] send failed ...`
3. For SMTP specifically, verify host resolution from inside backend container:
   ```bash
   docker exec pe_backend getent hosts <smtp-host>
   ```
   If unresolved, fix DNS/host value first.
4. Re-check SMTP fields in admin settings:
   - `smtpHost`, `smtpPort`, `smtpUsername`, `smtpFromEmail`
   - encrypted SMTP password present
   - auth/tls flags match your provider

If logs show `UnknownHostException`, the issue is DNS/host configuration, not the order/notification event flow.
