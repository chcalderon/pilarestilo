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
sudo git clone https://github.com/YOUR_ORG/PilarEstilo.git
sudo chown -R $USER:$USER /opt/PilarEstilo
cd /opt/PilarEstilo
```

If you use a private repo, set up a deploy key:
```bash
ssh-keygen -t ed25519 -C "deploy@pilarestilo" -f ~/.ssh/deploy_key -N ""
# Add the public key to GitHub → Settings → Deploy keys
```

---

## 4. Configure Environment

```bash
cp /opt/PilarEstilo/infra/.env.example /opt/PilarEstilo/infra/.env
nano /opt/PilarEstilo/infra/.env
```

Edit the following values at minimum:

```bash
POSTGRES_PASSWORD=a_long_random_password_here   # CHANGE THIS
DOMAIN=pilarestilo.com                          # your real domain
```

Protect the file from other users:
```bash
chmod 600 /opt/PilarEstilo/infra/.env
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
cd /opt/PilarEstilo
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Docker will:
1. Pull `postgres:16-alpine` and `caddy:2-alpine` images.
2. Build the backend image from `backend/Dockerfile`.
3. Build the frontend image from `frontend/Dockerfile`.
4. Start all four containers (`pe_postgres`, `pe_backend`, `pe_frontend`, `pe_caddy`).
5. Apply Flyway database migrations on backend startup.

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

Pull the latest code and rebuild:

```bash
cd /opt/PilarEstilo
git pull origin main

docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Docker Compose rebuilds only the services whose image changed and replaces containers one at a time. There is a brief downtime window (~5–15 seconds) while the backend container restarts.

**Zero-downtime strategy (future):** Run two backend replicas behind a load balancer and do a rolling restart. With a single VPS this is not worth the complexity in v1.

**Database migrations:** Flyway runs automatically on backend startup. Migrations are applied in order and are idempotent — re-running them on an already-migrated database is safe.

**Rollback:** To revert to the previous image, use `git checkout <previous-commit>` and re-run `docker compose up -d --build`.

---

## 10. Backup Strategy

### Manual backup

```bash
docker exec pe_postgres pg_dump -U pilar pilarestilo > /opt/backups/pilarestilo_$(date +%Y%m%d_%H%M%S).sql
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

**Future Prometheus integration (P5):**

Add `management.endpoints.web.exposure.include=health,info,metrics,prometheus` to `application.properties` and scrape `/api/actuator/prometheus` with Prometheus. Visualize in Grafana.

---

## 12. Local Development

### Without Docker

**Backend:**
```bash
# Prerequisites: Java 17+, Maven 3.9+, PostgreSQL running locally

cd backend/
mvn spring-boot:run -Dspring-boot.run.profiles=local
# Uses src/main/resources/application-local.properties
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
# From repo root — uses override file that skips Caddy and exposes ports directly
docker compose -f infra/docker-compose.yml up --build
```

Access the app at `http://localhost` (Caddy on port 80 with `DOMAIN=localhost`).

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

**Symptom:** `pe_backend` shows `unhealthy` or restart loops.

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
