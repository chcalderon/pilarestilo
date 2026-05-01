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

OLLAMA_MODEL="${APP_PRODUCT_AI_OLLAMA_MODEL:-}"
if [[ -z "${OLLAMA_MODEL}" ]]; then
  OLLAMA_MODEL="$(read_env_value APP_PRODUCT_AI_OLLAMA_MODEL)"
fi
if [[ -z "${OLLAMA_MODEL}" ]]; then
  OLLAMA_MODEL="gemma3"
fi

OLLAMA_FALLBACK_MODEL="${APP_PRODUCT_AI_OLLAMA_QUALITY_FALLBACK_MODEL:-}"
if [[ -z "${OLLAMA_FALLBACK_MODEL}" ]]; then
  OLLAMA_FALLBACK_MODEL="$(read_env_value APP_PRODUCT_AI_OLLAMA_QUALITY_FALLBACK_MODEL)"
fi

OLLAMA_MODELS="${APP_PRODUCT_AI_OLLAMA_MODELS:-}"
if [[ -z "${OLLAMA_MODELS}" ]]; then
  OLLAMA_MODELS="$(read_env_value APP_PRODUCT_AI_OLLAMA_MODELS)"
fi
if [[ -z "${OLLAMA_MODELS}" ]]; then
  OLLAMA_MODELS="${OLLAMA_MODEL}"
  if [[ -n "${OLLAMA_FALLBACK_MODEL}" ]]; then
    OLLAMA_MODELS="${OLLAMA_MODELS},${OLLAMA_FALLBACK_MODEL}"
  fi
fi

OLLAMA_AUTO_PULL_MODEL="${APP_PRODUCT_AI_OLLAMA_AUTO_PULL_MODEL:-}"
if [[ -z "${OLLAMA_AUTO_PULL_MODEL}" ]]; then
  OLLAMA_AUTO_PULL_MODEL="$(read_env_value APP_PRODUCT_AI_OLLAMA_AUTO_PULL_MODEL)"
fi
if [[ -z "${OLLAMA_AUTO_PULL_MODEL}" ]]; then
  OLLAMA_AUTO_PULL_MODEL="true"
fi

declare -a OLLAMA_MODELS_ARRAY=()
declare -A OLLAMA_MODELS_SEEN=()
IFS=',' read -r -a ollama_models_items <<< "${OLLAMA_MODELS}"
for item in "${ollama_models_items[@]}"; do
  model_trimmed="$(printf '%s' "${item}" | tr -d '[:space:]')"
  if [[ -z "${model_trimmed}" ]]; then
    continue
  fi
  if [[ -n "${OLLAMA_MODELS_SEEN[${model_trimmed}]+x}" ]]; then
    continue
  fi
  OLLAMA_MODELS_SEEN["${model_trimmed}"]=1
  OLLAMA_MODELS_ARRAY+=("${model_trimmed}")
done

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
echo "[deploy] Force recreate: ${FORCE_RECREATE}"
echo "[deploy] Remove orphans: ${REMOVE_ORPHANS}"
echo "[deploy] Reload caddy: ${RELOAD_CADDY}"
echo "[deploy] Ollama enabled: ${OLLAMA_ENABLED}"
echo "[deploy] Ollama models: ${OLLAMA_MODELS}"
echo "[deploy] Ollama auto-pull model: ${OLLAMA_AUTO_PULL_MODEL}"

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

if normalize_bool_true "${OLLAMA_ENABLED}" && normalize_bool_true "${OLLAMA_AUTO_PULL_MODEL}"; then
  echo "[deploy] Ensuring Ollama models are available..."
  if "${compose_cmd[@]}" ps --status running --services | grep -qx "ollama"; then
    max_wait_seconds=120
    waited_seconds=0
    until "${compose_cmd[@]}" exec -T ollama sh -c "wget -qO- http://localhost:11434/api/version >/dev/null 2>&1"; do
      if (( waited_seconds >= max_wait_seconds )); then
        echo "[deploy] WARNING: Ollama API not ready after ${max_wait_seconds}s; continuing." >&2
        break
      fi
      sleep 3
      waited_seconds=$((waited_seconds + 3))
    done

    for model in "${OLLAMA_MODELS_ARRAY[@]}"; do
      if "${compose_cmd[@]}" exec -T ollama ollama list | awk 'NR>1 {print $1}' | grep -Eq "^${model}(:|$)"; then
        echo "[deploy] Ollama model already present: ${model}"
      else
        echo "[deploy] Pulling Ollama model: ${model}"
        "${compose_cmd[@]}" exec -T ollama ollama pull "${model}"
      fi
    done
  else
    echo "[deploy] WARNING: Ollama service is not running; skipping model pull." >&2
  fi
fi

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
