#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DEPLOY="${SCRIPT_DIR}/local_deploy.sh"

if [[ ! -f "${LOCAL_DEPLOY}" ]]; then
  echo "[local-rebuild] ERROR: local_deploy.sh no encontrado en ${SCRIPT_DIR}" >&2
  exit 1
fi

echo "[local-rebuild] Bajando stack local..."
bash "${LOCAL_DEPLOY}" down --remove-orphans

echo "[local-rebuild] Subiendo stack local con build..."
bash "${LOCAL_DEPLOY}" up "$@"
