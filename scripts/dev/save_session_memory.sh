#!/usr/bin/env bash
set -euo pipefail

NOTE="${1:-}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MEMORY_FILE="${REPO_ROOT}/docs/session-memory.md"

cd "${REPO_ROOT}"

TIMESTAMP="$(date '+%Y-%m-%d %H:%M:%S %z')"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
COMMIT="$(git rev-parse --short HEAD)"
STATUS="$(git status --short || true)"

if [[ -z "${STATUS}" ]]; then
  STATUS="clean"
fi

if [[ ! -f "${MEMORY_FILE}" ]]; then
  cat > "${MEMORY_FILE}" <<'EOF'
# Session Memory

Bitacora de contexto tecnico para retomar trabajo sin perder continuidad.

EOF
fi

{
  echo "## ${TIMESTAMP}"
  echo
  echo "- Branch: \`${BRANCH}\`"
  echo "- Commit: \`${COMMIT}\`"
  if [[ -n "${NOTE}" ]]; then
    echo "- Note: ${NOTE}"
  fi
  echo "- Working tree:"
  echo '```text'
  echo "${STATUS}"
  echo '```'
  echo
} >> "${MEMORY_FILE}"

echo "[session-memory] actualizado: ${MEMORY_FILE}"
