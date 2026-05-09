#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
COMPOSE_FILE="infra/docker-compose.yml"
ENV_FILE="infra/.env"
ACTION="${1:-up}"
shift || true

SKIP_BUILD="${SKIP_BUILD:-false}"
FORCE_RECREATE="${FORCE_RECREATE:-false}"
REMOVE_ORPHANS="${REMOVE_ORPHANS:-true}"
LOCAL_DEPLOY_ALLOW_NONLOCAL="${LOCAL_DEPLOY_ALLOW_NONLOCAL:-false}"

cd "${APP_DIR}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[local-deploy] ERROR: ${COMPOSE_FILE} not found in ${APP_DIR}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[local-deploy] ERROR: ${ENV_FILE} not found. Create it from infra/.env.example first." >&2
  exit 1
fi

# Read DEPLOY_PROFILES from infra/.env if not set in current shell.
if [[ -z "${DEPLOY_PROFILES:-}" ]]; then
  DEPLOY_PROFILES=$(grep -E '^DEPLOY_PROFILES=' "${ENV_FILE}" | cut -d'=' -f2- | tr -d '[:space:]' || true)
fi

if [[ -n "${DEPLOY_PROFILES:-}" ]]; then
  sanitized_profiles=""
  IFS=',' read -r -a profile_items <<< "${DEPLOY_PROFILES}"
  for profile in "${profile_items[@]}"; do
    item="$(printf '%s' "${profile}" | tr -d '[:space:]')"
    if [[ -z "${item}" || "${item}" == "ai" ]]; then
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

is_local_domain() {
  local value="$1"
  local lowered
  lowered="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"
  [[ "${lowered}" == "localhost" || "${lowered}" == "127.0.0.1" || "${lowered}" == "::1" || "${lowered}" == *.localhost ]]
}

assert_local_only() {
  if normalize_bool_true "${LOCAL_DEPLOY_ALLOW_NONLOCAL}"; then
    return
  fi
  local raw_domain
  raw_domain="$(grep -E '^DOMAIN=' "${ENV_FILE}" | head -n 1 | cut -d'=' -f2- | tr -d '\r' || true)"
  if [[ -z "${raw_domain}" ]]; then
    return
  fi
  read -r -a domain_items <<< "${raw_domain}"
  local domain
  for domain in "${domain_items[@]}"; do
    if [[ -n "${domain}" ]] && ! is_local_domain "${domain}"; then
      echo "[local-deploy] ERROR: DOMAIN='${raw_domain}' parece entorno no local." >&2
      echo "[local-deploy] Este script es solo para local (localhost/127.0.0.1/*.localhost)." >&2
      echo "[local-deploy] Usa scripts/deploy/vps_deploy.sh para VPS/produccion." >&2
      echo "[local-deploy] Si necesitas forzar, exporta LOCAL_DEPLOY_ALLOW_NONLOCAL=true." >&2
      exit 1
    fi
  done
}

assert_local_only

if [[ -n "${DEPLOY_PROFILES}" ]]; then
  export COMPOSE_PROFILES="${DEPLOY_PROFILES}"
fi

compose_cmd=(docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "[local-deploy] App dir: ${APP_DIR}"
echo "[local-deploy] Action: ${ACTION}"
echo "[local-deploy] Profiles: ${DEPLOY_PROFILES:-<none>}"

case "${ACTION}" in
  up)
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
    compose_up_args+=("$@")
    "${compose_cmd[@]}" "${compose_up_args[@]}"
    "${compose_cmd[@]}" ps
    ;;
  down)
    "${compose_cmd[@]}" down "$@"
    ;;
  ps)
    "${compose_cmd[@]}" ps "$@"
    ;;
  logs)
    if [[ "$#" -eq 0 ]]; then
      "${compose_cmd[@]}" logs -f
    else
      "${compose_cmd[@]}" logs -f "$@"
    fi
    ;;
  restart)
    "${compose_cmd[@]}" restart "$@"
    "${compose_cmd[@]}" ps
    ;;
  *)
    echo "[local-deploy] Unknown action '${ACTION}'." >&2
    echo "Usage: bash scripts/deploy/local_deploy.sh [up|down|ps|logs|restart] [extra compose args/services]" >&2
    exit 1
    ;;
esac
