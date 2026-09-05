#!/usr/bin/env bash
# Retry the FAILED "Smoke H-2/H-3" batches in production after the Meta-adapter
# fix (75fa1bf) landed. Reads the admin password from $PE_ADMIN_PASS or prompts.
set -euo pipefail

API="${API:-https://pilarestilo.com/api}"
LABEL="Smoke H-2/H-3"

if [[ -z "${PE_TOKEN:-}" ]]; then
  if [[ -z "${PE_ADMIN_PASS:-}" ]]; then
    read -r -s -p "Admin password (admin@pilarestilo.com): " PE_ADMIN_PASS
    echo
  fi
  PE_TOKEN=$(curl -s -X POST "$API/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"admin@pilarestilo.com\",\"password\":\"${PE_ADMIN_PASS}\"}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
fi
[[ -n "$PE_TOKEN" ]] || { echo "no token"; exit 1; }
AUTH=(-H "Authorization: Bearer $PE_TOKEN")

show() {
  curl -s "${AUTH[@]}" "$API/admin/publications/batches" | python3 -c "
import sys,json
for b in json.load(sys.stdin):
    print(b['batchId'], 'sched:', b.get('scheduledAt'),
          'pub:', b.get('published'), 'fail:', b.get('failed'),
          'sch:', b.get('scheduled'), 'pend:', b.get('pending'),
          '|', b.get('campaignLabel'))
"
}

echo "=== batches before ==="; show

mapfile -t FAILED < <(curl -s "${AUTH[@]}" "$API/admin/publications/batches" | python3 -c "
import sys,json
for b in json.load(sys.stdin):
    if b.get('campaignLabel')=='$LABEL' and (b.get('failed') or 0) > 0:
        print(b['batchId'])
")

if [[ ${#FAILED[@]} -eq 0 ]]; then
  echo "no failed smoke batches to retry"
else
  for id in "${FAILED[@]}"; do
    echo "=== retry-failed $id ==="
    curl -s -X POST "${AUTH[@]}" "$API/admin/publications/batches/$id/retry-failed" | python3 -m json.tool
  done
fi

echo "=== batches after ==="; show
