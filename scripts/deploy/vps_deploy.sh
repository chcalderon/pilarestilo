#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/PilarEstilo}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
DEPLOY_PROFILES="${DEPLOY_PROFILES:-}"
SKIP_BUILD="${SKIP_BUILD:-false}"

COMPOSE_FILE="infra/docker-compose.yml"
ENV_FILE="infra/.env"

echo "[deploy] App dir: ${APP_DIR}"
echo "[deploy] Branch: ${DEPLOY_BRANCH}"
echo "[deploy] Profiles: ${DEPLOY_PROFILES:-<none>}"
echo "[deploy] Skip build: ${SKIP_BUILD}"

cd "${APP_DIR}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[deploy] ERROR: ${COMPOSE_FILE} not found in ${APP_DIR}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[deploy] ERROR: ${ENV_FILE} not found. Create it from infra/.env.example first." >&2
  exit 1
fi

echo "[deploy] Syncing repository..."
git fetch origin --prune
git checkout "${DEPLOY_BRANCH}"
git pull --ff-only origin "${DEPLOY_BRANCH}"

if [[ -n "${DEPLOY_PROFILES}" ]]; then
  export COMPOSE_PROFILES="${DEPLOY_PROFILES}"
fi

compose_cmd=(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "[deploy] Backing up database..."
BACKUP_DIR="${APP_DIR}/backups"
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/pre-deploy-$(date +%Y%m%d-%H%M%S).sql.gz"
source "${ENV_FILE}"
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T postgres \
  pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "${BACKUP_FILE}" && \
  echo "[deploy] Backup saved: ${BACKUP_FILE}" || \
  echo "[deploy] WARNING: backup failed (postgres may not be running yet), continuing..."
# Keep last 10 backups
ls -t "${BACKUP_DIR}"/pre-deploy-*.sql.gz 2>/dev/null | tail -n +11 | xargs rm -f || true

echo "[deploy] Pulling updated base images (best effort)..."
"${compose_cmd[@]}" pull || true

echo "[deploy] Applying stack changes..."
if [[ "${SKIP_BUILD}" == "true" ]]; then
  "${compose_cmd[@]}" up -d
else
  "${compose_cmd[@]}" up -d --build
fi

echo "[deploy] Service status:"
"${compose_cmd[@]}" ps

echo "[deploy] Done."
