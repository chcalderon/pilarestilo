#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/pilarestilo}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
SKIP_BUILD="${SKIP_BUILD:-false}"
FORCE_RECREATE="${FORCE_RECREATE:-true}"
REMOVE_ORPHANS="${REMOVE_ORPHANS:-true}"
RELOAD_CADDY="${RELOAD_CADDY:-true}"

COMPOSE_FILE="infra/docker-compose.yml"
ENV_FILE="infra/.env"

cd "${APP_DIR}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[deploy] ERROR: ${COMPOSE_FILE} not found in ${APP_DIR}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[deploy] ERROR: ${ENV_FILE} not found. Create it from infra/.env.example first." >&2
  exit 1
fi

# Read DEPLOY_PROFILES from infra/.env if not set in the environment.
if [[ -z "${DEPLOY_PROFILES:-}" ]]; then
  DEPLOY_PROFILES=$(grep -E '^DEPLOY_PROFILES=' "${ENV_FILE}" | cut -d'=' -f2- | tr -d '[:space:]' || true)
fi

if [[ -n "${DEPLOY_PROFILES:-}" ]]; then
  sanitized_profiles=""
  IFS=',' read -r -a profile_items <<< "${DEPLOY_PROFILES}"
  for profile in "${profile_items[@]}"; do
    item="$(printf '%s' "${profile}" | tr -d '[:space:]')"
    # "quality" runs SonarQube, which is a tool for reading the code on a developer's machine,
    # not something the shop serves. Dropped here as well as being off by default, so a profile
    # list copied from a local .env cannot start a 2 GB Java process on the VPS.
    if [[ -z "${item}" || "${item}" == "ai" || "${item}" == "quality" ]]; then
      continue
    fi
    if [[ -z "${sanitized_profiles}" ]]; then
      sanitized_profiles="${item}"
    else
      sanitized_profiles="${sanitized_profiles},${item}"
    fi
  done
  DEPLOY_PROFILES="${sanitized_profiles}"
fi

normalize_bool_true() {
  local raw="$1"
  local normalized
  normalized=$(printf '%s' "${raw}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
  [[ "${normalized}" == "true" || "${normalized}" == "1" || "${normalized}" == "yes" || "${normalized}" == "on" ]]
}

echo "[deploy] App dir: ${APP_DIR}"
echo "[deploy] Branch: ${DEPLOY_BRANCH}"
echo "[deploy] Profiles: ${DEPLOY_PROFILES:-<none>}"
echo "[deploy] Skip build: ${SKIP_BUILD}"
echo "[deploy] Force recreate: ${FORCE_RECREATE}"
echo "[deploy] Remove orphans: ${REMOVE_ORPHANS}"
echo "[deploy] Reload caddy: ${RELOAD_CADDY}"

echo "[deploy] Syncing repository..."
git fetch origin --prune
git checkout "${DEPLOY_BRANCH}"
git pull --ff-only origin "${DEPLOY_BRANCH}"

if [[ -n "${DEPLOY_PROFILES}" ]]; then
  export COMPOSE_PROFILES="${DEPLOY_PROFILES}"
fi

compose_cmd=(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "[deploy] Pulling updated base images (best effort)..."
"${compose_cmd[@]}" pull || true

echo "[deploy] Applying stack changes..."
compose_up_args=(up -d)
if [[ "${SKIP_BUILD}" != "true" ]]; then
  compose_up_args+=(--build)
fi
if normalize_bool_true "${FORCE_RECREATE}"; then
  compose_up_args+=(--force-recreate)
fi
if normalize_bool_true "${REMOVE_ORPHANS}"; then
  compose_up_args+=(--remove-orphans)
fi
"${compose_cmd[@]}" "${compose_up_args[@]}"

if normalize_bool_true "${RELOAD_CADDY}"; then
  echo "[deploy] Reloading Caddy config..."
  if "${compose_cmd[@]}" ps --status running --services | grep -qx "caddy"; then
    if ! "${compose_cmd[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile; then
      echo "[deploy] WARNING: caddy reload failed; restarting caddy container." >&2
      "${compose_cmd[@]}" restart caddy
    fi
  else
    echo "[deploy] WARNING: Caddy service is not running; skipping reload." >&2
  fi
fi

echo "[deploy] Service status:"
"${compose_cmd[@]}" ps

echo "[deploy] Done."
