#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/pilarestilo}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
SKIP_BUILD="${SKIP_BUILD:-false}"

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

read_env_value() {
  local key="$1"
  local value=""
  value=$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d'=' -f2- || true)
  printf '%s' "${value}"
}

normalize_bool_true() {
  local raw="$1"
  local normalized
  normalized=$(printf '%s' "${raw}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
  [[ "${normalized}" == "true" || "${normalized}" == "1" || "${normalized}" == "yes" || "${normalized}" == "on" ]]
}

profile_exists() {
  local needle="$1"
  local csv="$2"
  local item
  IFS=',' read -r -a items <<< "${csv}"
  for item in "${items[@]}"; do
    if [[ "$(printf '%s' "${item}" | tr -d '[:space:]')" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

OLLAMA_ENABLED="${APP_PRODUCT_AI_OLLAMA_ENABLED:-}"
if [[ -z "${OLLAMA_ENABLED}" ]]; then
  OLLAMA_ENABLED="$(read_env_value APP_PRODUCT_AI_OLLAMA_ENABLED)"
fi
if [[ -z "${OLLAMA_ENABLED}" ]]; then
  OLLAMA_ENABLED="true"
fi

if normalize_bool_true "${OLLAMA_ENABLED}"; then
  if [[ -z "${DEPLOY_PROFILES}" ]]; then
    DEPLOY_PROFILES="ai"
  elif ! profile_exists "ai" "${DEPLOY_PROFILES}"; then
    DEPLOY_PROFILES="${DEPLOY_PROFILES},ai"
  fi
fi

echo "[deploy] App dir: ${APP_DIR}"
echo "[deploy] Branch: ${DEPLOY_BRANCH}"
echo "[deploy] Profiles: ${DEPLOY_PROFILES:-<none>}"
echo "[deploy] Skip build: ${SKIP_BUILD}"
echo "[deploy] Ollama enabled: ${OLLAMA_ENABLED}"

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
if [[ "${SKIP_BUILD}" == "true" ]]; then
  "${compose_cmd[@]}" up -d
else
  "${compose_cmd[@]}" up -d --build
fi

echo "[deploy] Service status:"
"${compose_cmd[@]}" ps

echo "[deploy] Done."
